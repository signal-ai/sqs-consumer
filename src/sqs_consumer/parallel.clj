(ns sqs-consumer.parallel
  (:require [com.climate.claypoole :as cp]
            [sqs-consumer.core :as core]
            [sqs-consumer.sequential :as sequential]))

(set! *warn-on-reflection* true)

(defn parallel-process [process-fn]
  (fn [{:keys [config messages]}]
    (cp/upmap
     (:thread-pool config)
     (fn [message] (process-fn (sequential/extract-message config message)))
     messages)))

(def with-auto-delete sequential/with-auto-delete)

(def with-error-handling sequential/with-error-handling)

(defn create-consumer [& {:keys [threadpool-size process-fn]
                          :or {threadpool-size 10} :as args}]
  (let [thread-pool (cp/threadpool threadpool-size)
        consumer (core/create-consumer (merge args {:thread-pool thread-pool :process-fn (parallel-process process-fn)}))]
    (-> consumer
        (update :stop-consumer (fn [stop-fn]
                                 (fn []
                                   (cp/shutdown thread-pool)
                                   (stop-fn)))))))
