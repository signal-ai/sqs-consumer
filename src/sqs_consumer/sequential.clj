(ns sqs-consumer.sequential
  (:require [amazonica.aws.sqs :as sqs]))

(defn delete-message [{:keys [queue-url aws-config]} receipt-handle]
  (sqs/delete-message aws-config queue-url receipt-handle))

(defn sequential-process [process-fn]
  (fn [{:keys [config messages]}]
    (let [{:keys [queue-url aws-config]} config]
      (run! (fn [message] (process-fn {:message (:body message)
                                       :delete-message #(delete-message config (:receipt-handle message))}))
            messages))))

(defn with-auto-delete [process-fn]
  (fn [{:keys [delete-message] :as message}]
    (when (process-fn (dissoc message :delete-message))
      (delete-message))))

(defn with-decoder [process-fn decoder]
  (fn [{:keys [message]}]
    (process-fn (decoder message))))

(defn with-error-handling [process-fn error-handler]
  (fn [message]
    (try
      (process-fn message)
      (catch Exception e
        (error-handler e)))))
