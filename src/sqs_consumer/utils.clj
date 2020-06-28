(ns sqs-consumer.utils
  (:require [clojure.data.json :as json]
            [amazonica.aws.sqs :as sqs]))

(defn add-timestamp [message outer-message]
  "Add timestamp from the SNS metadata if it doesn't already exist"
  (if (:timestamp message)
    message
    (assoc message :timestamp (:Timestamp outer-message))))

(defn decode-sns-encoded-json [message-body]
  (let [outer-message (json/read-str message-body :key-fn keyword)]
    (-> outer-message
        :Message
        (json/read-str :key-fn keyword)
        (add-timestamp outer-message))))

(defn with-message-decoder [process-fn decoder]
  (fn [{:keys [message-body] :as message}]
    (-> message
        (assoc :message (decoder message-body))
        process-fn)))

(defn with-error-handler [process-fn error-handler]
  (fn [message]
    (try
      (process-fn message)
      (catch Exception ex
        (error-handler ex)))))

(defn uuid [] (str (java.util.UUID/randomUUID)))
