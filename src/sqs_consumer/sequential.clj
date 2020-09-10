(ns sqs-consumer.sequential
  (:require [amazonica.aws.sqs :as sqs]
            [sqs-consumer.core :as core]
            [sqs-consumer.utils :as utils]))

(defn delete-message [{:keys [queue-url aws-config]} receipt-handle]
  (sqs/delete-message aws-config queue-url receipt-handle))

(defn sequential-process [process-fn]
  (fn [{:keys [config messages]}]
    (run! (fn [message] (process-fn {:message-body (:body message)
                                     :delete-message #(delete-message config (:receipt-handle message))}))
          messages)))

(defn with-auto-delete [process-fn]
  (fn [{:keys [delete-message] :as message}]
    (when (process-fn (dissoc message :delete-message))
      (delete-message))))

(defn with-decoder [process-fn decoder]
  (fn [{:keys [message]}]
    (process-fn (decoder message))))

(def with-error-handling utils/with-error-handler)
;; TODO: this should also wrap `sequential-process`
(def create-consumer core/create-consumer)
