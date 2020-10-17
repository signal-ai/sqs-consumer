(ns sqs-consumer.batch
  (:require [amazonica.aws.sqs :as sqs]
            [sqs-consumer.core :as core]
            [sqs-consumer.utils :as utils])
  (:import [com.amazonaws.services.sqs.model DeleteMessageBatchRequestEntry]))

(defn delete-batch [{:keys [queue-url aws-config]} messages]
  (sqs/delete-message-batch
   aws-config
   :queue-url queue-url
   :entries (map (fn [msg] (select-keys msg [:message-id :recipient-handle])) messages)))

(defn batch-process [process-fn]
  (fn [{:keys [config messages]}]
    (let [{:keys [queue-url aws-config]} config]
      (process-fn {:messages (map :body messages)
                   :delete-messages #(delete-batch config messages)
                   :change-message-visibility (fn [visibility])}))))

(defn with-auto-delete [process-fn]
  (fn [{:keys [delete-messages] :as batch}]
    (when (process-fn (dissoc batch :delete-messages))
      (delete-messages))))

(defn with-decoder [process-fn decoder]
  (fn [{:keys [messages]}]
    (process-fn (doall (map decoder messages)))))

(def with-error-handling utils/with-error-handler)
;; TODO: this should also wrap `batch-process`
(defn create-consumer [& {:keys [] :as args}]
  (core/create-consumer args))
