(ns sqs-consumer.core
  (:require [amazonica.aws.sqs :as sqs]))

(defn process [queue-url f msg]
  (let [{:keys [receipt-handle body]} msg]
    (f {:message-body body
        :delete-fn #(sqs/delete-message queue-url receipt-handle)})))

(defn dequeue [{:keys [queue-url wait-time-seconds max-number-of-messages]} f]
  (when-let [msgs (:messages (sqs/receive-message :queue-url queue-url
                                                  :wait-time-seconds wait-time-seconds
                                                  :max-number-of-messages max-number-of-messages
                                                  :visibility-timeout 1800))]
    (doall (pmap (partial process queue-url f) msgs))))

(defn create-consumer [& {:keys [queue-url
                                 max-number-of-messages
                                 wait-time-seconds
                                 shutdown-wait-time-ms
                                 process-fn]
                          :or {shutdown-wait-time-ms 2000
                               wait-time-seconds 10}
                          }]
  ;; TODO: validate parameters
  (let [config {
                :queue-url queue-url
                :max-number-of-messages max-number-of-messages
                :wait-time-seconds wait-time-seconds
                :shutdown-wait-time-ms shutdown-wait-time-ms
                :process-fn process-fn
                :running (atom false)
                :finished-shutdown (atom false)
                }]
    {:config config
     :start-consumer (fn []
                       (reset! (:running config) true)
                       (while @(:running config)
                         (dequeue config process-fn))
                       (reset! (:finished-shutdown config) true))
     :stop-consumer (fn []
                      (reset! (:running config) false)
                      ; wait up to specified amount of time for messages in flight to finish processing
                      (loop [shutdown-time shutdown-wait-time-ms]
                        (when (and (not @(:finished-shutdown config)) ; consumer hasn't shutdown gracefully yet
                                   (pos? shutdown-time))               ; and we can still wait longer
                          (Thread/sleep 100)
                          (recur (- shutdown-time 100))))
                      )}))
