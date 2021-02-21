(ns sqs-consumer.sequential
  (:require [amazonica.aws.sqs :as sqs]
            [sqs-consumer.core :as core]))

(set! *warn-on-reflection* true)

(defn- delete-message [{:keys [queue-url aws-config]} receipt-handle]
  (sqs/delete-message aws-config queue-url receipt-handle))

(defn- change-message-visibility [{:keys [queue-url aws-config]} receipt-handle visibility-timeout]
  (sqs/change-message-visibility aws-config queue-url receipt-handle visibility-timeout))

(defn extract-message
  "Extracts the message from the SQS receive messages response.
   
   Each message is an SQS Message https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/index.html?com/amazonaws/services/sqs/model/Message.html."
  [config message]
  {;; the original message, with unparsed body.
   :message message
   ;; attributes on the message, note these are different to message-attributes, e.g. ApproximateReceiveCount
   :attributes (:attributes message)
   ;; the message body itself. If this was the message from SNS it is wrapped in an SNS envelope unless RawMessageDelivery is set to true
   :message-body (:body message)
   ;; map of any message attributes set via the SQS send-message API
   :message-attributes (core/parse-message-attributes (:message-attributes message))
   ;; the sqs delete message API call pre-bound to the given message/aws config
   :delete-message #(delete-message config (:receipt-handle message))
    ;; the sqs change message visibility API call pre-bound to the given message/AWS config
   :change-message-visibility #(change-message-visibility config (:receipt-handle message) %)})

(defn sequential-process [process-fn]
  (fn [{:keys [config messages]}]
    (run! (fn [message] (process-fn (extract-message config message)))
          messages)))

(defn with-auto-delete
  "Auto deletes the message from SQS whenever process-fn returns a truthy value.
   If a non-truthy value is returned or an exception is thrown the message will not be deleted."
  [process-fn]
  (fn [{:keys [delete-message] :as message}]
    (when (process-fn (dissoc message :delete-message))
      (delete-message))))

(defn with-decoder
  "Merges the message with the result of the given message decoder. The decoder is called with the :message-body from the extract-message function."
  [process-fn decoder]
  (fn [message]
    (-> message
        (merge (decoder (:message-body message)))
        process-fn)))

(defn with-error-handling
  "Calls the given error handler when any error occurs further down the handler stack."
  [process-fn error-handler]
  (fn [message]
    (try
      (process-fn message)
      (catch Exception ex
        (error-handler ex)))))

;; TODO: this should also wrap `sequential-process`
(defn create-consumer [& {:keys [process-fn] :as args}]
  (core/create-consumer (merge args {:process-fn (sequential-process process-fn)})))
