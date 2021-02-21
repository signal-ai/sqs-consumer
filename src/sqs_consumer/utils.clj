(ns sqs-consumer.utils
  (:require [clojure.tools.logging :as log]
            [lazy-require.core :as lreq]
            [sqs-consumer.core :as core]))

(set! *warn-on-reflection* true)

(defn- add-timestamp
  "Add timestamp from the SNS metadata if it doesn't already exist."
  [message-body outer-message]
  (if (:timestamp message-body)
    message-body
    (assoc message-body :timestamp (:Timestamp outer-message))))

(defn- lazy-load-data-json []
  (try
    (lreq/with-lazy-require [[clojure.data.json :as json]]
      #(clojure.data.json/read-str % :key-fn keyword))
    (catch Throwable e
      (log/error e "decoder called with no json-fn and without clojure.data.json on the classpath. Please either add clojure.data.json or provide a json-fn.")
      (throw e))))

(defn decode-sns-encoded-json
  "Returns a decoder which decodes the external SNS message and the internal \"Message\" property on the message, as well as parsing any message attributes
   which may be in the message body.

   Not required if RawMessageDelivery is true on the SNS topic.
   
   If no json-fn is provided, will load clojure.data.json."
  ([json-fn]
   (fn [message]
     (let [;; we may have already decoded this if wrapped with auto-decode-json-message
           outer-message (if (string? message) (json-fn message) message)]
       {:message-body (-> outer-message
                          :Message
                          (json-fn)
                          (add-timestamp outer-message))
        :message-attributes (->> outer-message
                                 :MessageAttributes
                                 (core/parse-message-attributes))
        :sqs-consumer/metadata {:message-envelope :sns}})))
  ([]
   (decode-sns-encoded-json (lazy-load-data-json))))

(defn decode-sqs-encoded-json
  "Returns a decoder which decodes the sqs message as json.
   
   If no json-fn is provided, will load clojure.data.json."
  ([json-fn]
   (fn [message]
     {;; we may have already decoded this if wrapped with auto-decode-json-message
      :message-body (if (string? message) (json-fn message) message)
      :sqs-consumer/metadata {:message-envelope :sqs}}))
  ([]
   (decode-sqs-encoded-json (lazy-load-data-json))))

(defn auto-decode-json-message
  "Returns a decoder determines whether the message was from SQS or SNS and decodes the message appropriately.
   
   Adds :sqs-consumer/metadata :message-envelope as :sqs or :sns to the decoded message for use by downstream handlers.
  
   If no json-fn is provided, will load clojure.data.json."
  ([json-fn]
   (let [parse-sns-message (decode-sns-encoded-json json-fn)
         parse-sqs-message (decode-sqs-encoded-json json-fn)]
     (fn [message]
       (let [outer-message (json-fn message)
             is-sns? (and (= "Notification" (:Type outer-message))
                          (contains? outer-message :TopicArn))]
         (cond
           is-sns? (do (log/debug "Decoding message from SNS" :sns-message-id (:MessageId outer-message))
                       (parse-sns-message outer-message))
           :else (do (log/debug "Decoding message from SQS")
                     (parse-sqs-message outer-message)))))))
  ([]
   (auto-decode-json-message (lazy-load-data-json))))

(defn uuid
  "Generates a V4 UUID and converts it to a string."
  []
  (str (java.util.UUID/randomUUID)))
