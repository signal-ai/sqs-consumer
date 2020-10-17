(ns sqs-consumer.parallel
  (:require [amazonica.aws.sqs :as sqs]
            [com.climate.claypoole :as cp]
            [sqs-consumer.core :as core]
            [sqs-consumer.utils :as utils]
            [sqs-consumer.sequential :as sequential]))

(defn parallel-process [process-fn]
  (fn [{:keys [config messages]}]
    (cp/upmap
     (:thread-pool config)
     (fn [message] (process-fn {:message-body (:body message)
                                :delete-message #(sequential/delete-message config (:receipt-handle message))}))
     messages)))

(def with-auto-delete sequential/with-auto-delete)

(def with-decoder sequential/with-decoder)

(def with-error-handling utils/with-error-handler)

(defn create-consumer [& {:keys [threadpool-size aws-config] :or {threadpool-size 10} :as args}]
  (let [thread-pool (cp/threadpool threadpool-size)
        consumer (core/create-consumer (merge args {:thread-pool thread-pool}))]
    (-> consumer
        (update :stop-consumer (fn [stop-fn]
                                 (fn []
                                   (cp/shutdown thread-pool)
                                   (stop-fn)))))))
