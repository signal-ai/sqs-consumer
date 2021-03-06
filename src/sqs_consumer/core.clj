(ns sqs-consumer.core
  (:require [amazonica.aws.sqs :as sqs]
            [clojure.tools.logging :as log]))

(set! *warn-on-reflection* true)

(defn dequeue [{:keys [attribute-names message-attribute-names queue-url wait-time-seconds max-number-of-messages aws-config visibility-timeout] :as config} f]
  (when-let [msgs (-> (sqs/receive-message aws-config
                                           :queue-url queue-url
                                           :attribute-names attribute-names
                                           :message-attribute-names message-attribute-names
                                           :wait-time-seconds wait-time-seconds
                                           :max-number-of-messages max-number-of-messages
                                           :visibility-timeout visibility-timeout)
                      :messages
                      seq)]
    (f {:config config :messages msgs})))

(defn get-queue-url [aws-config name]
  (:queue-url (sqs/get-queue-url aws-config name)))

(defn- default-top-level-error-handler [dequeue-fn]
  (try
    (dequeue-fn)
    (catch Exception e
      (log/error e "Failed to receive message from SQS queue")
      (Thread/sleep 1000))))

(defn parse-message-attributes
  "Resolves message attributes into a map.
   SQS message attributes are a hash-map of Key: {:Type ... :Value ...}, see https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-metadata.html#sqs-message-attributes
   Note: SQS message attributes will only be set on the received message directly if the message is either
      1. Published to SNS directly
      2. Received from an SNS topic with RawMessageDelivery set to true (https://docs.aws.amazon.com/sns/latest/dg/sns-large-payload-raw-message-delivery.html)"
  [message-attributes]
  (reduce (fn [attributes [key {value :Value}]] (assoc attributes key value)) {} message-attributes))


(defn create-consumer
  "run-dequeue-fn functions that wraps message dequeuing.
   Defaults to sleeping 1 second on exceptions."
  [{:keys [queue-url
           queue-name
           attribute-names
           message-attribute-names
           max-number-of-messages
           wait-time-seconds
           shutdown-wait-time-ms
           process-fn
           aws-config
           visibility-timeout
           run-dequeue-fn
           thread-pool ; optional. Only present during parallel processing
           ]
    :or {attribute-names ["All"]
         message-attribute-names ["All"]
         shutdown-wait-time-ms 2000
         wait-time-seconds 10
         visibility-timeout 60
         run-dequeue-fn default-top-level-error-handler
         aws-config {:client-config {}}}}]
  ;; TODO: validate parameters
  (let [queue-url (or queue-url (get-queue-url aws-config queue-name))
        config {:queue-url queue-url
                :attribute-names attribute-names
                :message-attribute-names message-attribute-names
                :max-number-of-messages max-number-of-messages
                :wait-time-seconds wait-time-seconds
                :shutdown-wait-time-ms shutdown-wait-time-ms
                :process-fn process-fn
                :running (atom false)
                :finished-shutdown (atom false)
                :aws-config aws-config
                :visibility-timeout visibility-timeout
                :thread-pool thread-pool}]
    (when (nil? queue-url)
      (throw (new IllegalArgumentException "Queue URL (:queue-url) or Queue Name (:queue-name) must be provided")))
    {:config config
     :start-consumer (fn []
                       (reset! (:running config) true)
                       (while @(:running config)
                         (run-dequeue-fn
                          #(dequeue config process-fn)))
                       (reset! (:finished-shutdown config) true))
     :stop-consumer (fn []
                      (reset! (:running config) false)
                      ; wait up to specified amount of time for messages in flight to finish processing
                      (loop [shutdown-time shutdown-wait-time-ms]
                        (when (and (not @(:finished-shutdown config)) ; consumer hasn't shutdown gracefully yet
                                   (pos? shutdown-time))              ; and we can still wait longer
                          (Thread/sleep 100)
                          (recur (- shutdown-time 100)))))
     :running (fn [] (and (not @(:running config)) @(:finished-shutdown config)))}))
