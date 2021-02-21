(ns sqs-consumer.utils
  (:require [clojure.tools.logging :as log]
            [lazy-require.core :as lreq]))

(defn add-timestamp
  "Add timestamp from the SNS metadata if it doesn't already exist."
  [message outer-message]
  (if (:timestamp message)
    message
    (assoc message :timestamp (:Timestamp outer-message))))

(defn- lazy-load-data-json []
  (try
    (lreq/with-lazy-require [[clojure.data.json :as json]]
      #(clojure.data.json/read-str % :key-fn keyword))
    (catch Throwable e
      (log/error e "decoder called with no json-fn and without clojure.data.json on the classpath. Please either add clojure.data.json or provide a json-fn.")
      (throw e))))

(defn decode-sns-encoded-json
  "Returns a decoder which decodes the external SNS message and the internal \"Message\" property on the message in-place.
   
   If no json-fn is provided, will load clojure.data.json."
  ([json-fn]
   (fn [message-body]
     (let [outer-message (json-fn message-body)]
       (-> outer-message
           :Message
           (json-fn)
           (add-timestamp outer-message)))))
  ([]
   (decode-sns-encoded-json (lazy-load-data-json))))

(defn decode-sqs-encoded-json
  "Returns a decoder which internal message in-place.
   
   If no json-fn is provided, will load clojure.data.json."
  ([json-fn]
   (fn [message-body]
     (-> message-body
         :Message
         (json-fn))))
  ([]
   (decode-sns-encoded-json (lazy-load-data-json))))

(defn auto-decode-json-message
  "Returns a decoder determines whether the message was from SQS or SNS and decodes the message appropriately.
   
   Adds :sqs-consumer/metadata :message-envelope as :sqs or :sns to the decoded message for use by downstream handlers.
  
   If no json-fn is provided, will load clojure.data.json."
  ([json-fn]
   (fn [message-body]
     (let [outer-message (json-fn message-body)]
       (cond
         (contains? outer-message :Type) (do (log/debug "Decoding message from SNS" :sns-message-id (:MessageId outer-message))
                                             (-> outer-message
                                                 :Message
                                                 (json-fn)
                                                 (add-timestamp outer-message)
                                                 (assoc-in [:sqs-consumer/metadata :message-envelope] :sns)))
         :else (do (log/debug "Decoding message from SQS")
                   (-> outer-message
                       (assoc-in [:sqs-consumer/metadata :message-envelope] :sqs)))))))
  ([]
   (auto-decode-json-message (lazy-load-data-json))))

(defn with-message-decoder
  "Assocs :message with the result of the given message decoder, called with the message body."
  [process-fn decoder]
  (fn [{:keys [message-body] :as message}]
    (-> message
        (assoc :message (decoder message-body))
        process-fn)))

(defn with-error-handler
  "Calls the given error handler when any error occurs further down the handler stack."
  [process-fn error-handler]
  (fn [message]
    (try
      (process-fn message)
      (catch Exception ex
        (error-handler ex)))))

(defn uuid [] (str (java.util.UUID/randomUUID)))
