(ns sqs-consumer.batch
  (:require [amazonica.aws.sqs :as sqs]
            [sqs-consumer.core :as core]
            [sqs-consumer.sequential :as sequential]))

(set! *warn-on-reflection* true)

(defn delete-batch [{:keys [queue-url aws-config]} messages]
  (sqs/delete-message-batch
   aws-config
   :queue-url queue-url
   :entries (map (fn [msg] (select-keys msg [:message-id :recipient-handle])) messages)))

(defn batch-process [process-fn]
  (fn [{:keys [config messages]}]
    (process-fn {;; TODO: pass through non-body
                 :messages (map :body messages)
                 :delete-messages #(delete-batch config messages)
                 :change-message-visibility (fn [_])})))

(defn with-auto-delete [process-fn]
  (fn [{:keys [delete-messages] :as batch}]
    (when (process-fn (dissoc batch :delete-messages))
      (delete-messages))))

(defn with-decoder [process-fn decoder]
  (fn [{:keys [messages]}]
    (process-fn (doall (map #(:message-body (decoder %)) messages)))))

(def with-error-handling sequential/with-error-handling)

;; TODO: this should also wrap `batch-process`
(defn create-consumer [& {:keys [process-fn] :as args}]
  (core/create-consumer (merge args {:process-fn (batch-process process-fn)})))
