(ns sqs-consumer.batch
  (:require [amazonica.aws.sqs :as sqs]
            [sqs-consumer.core :as core]
            [sqs-consumer.utils :as utils]))

(defn delete-batch [{:keys [queue-url aws-config]} messages]
  (sqs/delete-message-batch
   aws-config
   :queue-url queue-url
   :entries (map (fn [msg] (select-keys msg [:message-id :recipient-handle])) messages)))

(defn batch-process [process-fn]
  (fn [{:keys [config messages]}]
    (process-fn {:messages (map :body messages)
                 :delete-messages #(delete-batch config messages)
                 :change-message-visibility (fn [_])})))

(defn with-auto-delete [process-fn]
  (fn [{:keys [delete-messages] :as batch}]
    (when (process-fn (dissoc batch :delete-messages))
      (delete-messages))))

(defn with-decoder [process-fn decoder]
  (fn [{:keys [messages]}]
    (process-fn (doall (map decoder messages)))))

(def with-error-handling utils/with-error-handler)
;; TODO: this should also wrap `batch-process`
(defn create-consumer [& {:keys [process-fn] :as args}]
  (core/create-consumer (merge args {:process-fn (batch-process process-fn)})))
