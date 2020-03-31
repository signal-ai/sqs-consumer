(ns sqs-consumer.core
  (:require [amazonica.aws.sqs :as sqs]))

(defn process [{:keys [queue-url aws-config]} f msg]
  (let [{:keys [receipt-handle body]} msg]
    (f {:message-body body
        :delete-fn #(sqs/delete-message aws-config queue-url receipt-handle)})))

(defn dequeue [{:keys [queue-url wait-time-seconds max-number-of-messages aws-config visibility-timeout] :as config} f]
  (when-let [msgs (:messages (sqs/receive-message aws-config
                                                  :queue-url queue-url
                                                  :wait-time-seconds wait-time-seconds
                                                  :max-number-of-messages max-number-of-messages
                                                  :visibility-timeout visibility-timeout))]
    (doall (map (partial process config f) msgs))))

(defn get-queue-url [aws-config name]
  (:queue-url (sqs/get-queue-url aws-config name)))

(defn create-consumer [& {:keys [queue-url
                                 queue-name
                                 max-number-of-messages
                                 wait-time-seconds
                                 shutdown-wait-time-ms
                                 process-fn
                                 aws-config
                                 visibility-timeout]
                          :or {shutdown-wait-time-ms 2000
                               wait-time-seconds 10
                               visibility-timeout 60
                               aws-config {:client-config {}}}
                          }]
  ;; TODO: validate parameters
  (let [queue-url (or queue-url (get-queue-url aws-config queue-name))
        config {
                :queue-url queue-url
                :max-number-of-messages max-number-of-messages
                :wait-time-seconds wait-time-seconds
                :shutdown-wait-time-ms shutdown-wait-time-ms
                :process-fn process-fn
                :running (atom false)
                :finished-shutdown (atom false)
                :aws-config aws-config
                :visibility-timeout visibility-timeout
                }]
    (when (nil? queue-url)
      (throw (new IllegalArgumentException "Queue URL or Queue Name must be provided")))
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
                                   (pos? shutdown-time))              ; and we can still wait longer
                          (Thread/sleep 100)
                          (recur (- shutdown-time 100))))
                      )}))
