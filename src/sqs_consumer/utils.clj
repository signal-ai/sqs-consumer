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
    (log/debug "Lazy loading clojure.data.json")
    #_:clj-kondo/ignore
    (lreq/with-lazy-require [[clojure.data.json :as json]]
      #(clojure.data.json/read-str % :key-fn keyword))
    (catch Throwable e
      (log/error e "decoder called with no json-fn and without clojure.data.json on the classpath. Please either add clojure.data.json or provide a json-fn.")
      (throw e))))

(defn sns-encoded-json-decoder
  "Returns a decoder which decodes the external SNS message and the internal \"Message\" property on the message, as well as parsing any message attributes
   which may be in the message body.
   
   Adds :sqs-consumer.core/metadata :message-envelope as :sns to the decoded message for use by downstream handlers.

   Not required if RawMessageDelivery is true on the SNS topic.
   
   If no json-fn is provided, will load clojure.data.json."
  ([json-fn]
   (fn decode-sns-encoded-json [{:keys [message-body] :as message}]
     (let [;; we may have already decoded this if wrapped with with-auto-json-decoder
           outer-message (if (string? message-body) (json-fn message-body) message-body)]
       (-> message (merge {:message-body (-> outer-message
                                             :Message
                                             (json-fn)
                                             (add-timestamp outer-message))
                           :message-attributes (->> outer-message
                                                    :MessageAttributes
                                                    (core/parse-message-attributes))})
           (assoc-in [::core/metadata :message-envelope] :sns)))))
  ([]
   (sns-encoded-json-decoder (lazy-load-data-json))))

(defn sqs-encoded-json-decoder
  "Returns a decoder which decodes the sqs message as json.
   
   Adds :sqs-consumer.core/metadata :message-envelope as :sqs to the decoded message for use by downstream handlers.

   If no json-fn is provided, will load clojure.data.json."
  ([json-fn]
   (fn decode-sqs-encoded-json [{:keys [message-body] :as message}]
     (-> message (merge {;; we may have already decoded this if wrapped with with-auto-json-decoder
                         :message-body (if (string? message-body) (json-fn message-body) message-body)})
         (assoc-in [::core/metadata :message-envelope] :sqs))))
  ([]
   (sqs-encoded-json-decoder (lazy-load-data-json))))


(defn auto-json-decoder
  "Returns a decoder determines whether the message was from SQS or SNS and decodes the message appropriately.
   
   Adds :sqs-consumer.core/metadata :message-envelope as :sns or :sqs ()depending on the envelope) to the decoded message for use by downstream handlers.

   Not required if RawMessageDelivery is true on the SNS topic.
  
   If no json-fn is provided, will load clojure.data.json."
  ([json-fn]
   (let [parse-sns-message (sns-encoded-json-decoder json-fn)
         parse-sqs-message (sqs-encoded-json-decoder json-fn)]
     (fn [{:keys [message-body] :as message}]
       (let [outer-message (json-fn message-body)
             message-with-parsed-body (assoc message :message-body outer-message)
             is-sns? (and (= "Notification" (:Type outer-message))
                          (contains? outer-message :TopicArn))]
         (cond
           is-sns? (do (log/debug "Decoding message from SNS" :sns-message-id (:MessageId outer-message))
                       (parse-sns-message message-with-parsed-body))
           :else (do (log/debug "Decoding message from SQS")
                     (parse-sqs-message message-with-parsed-body)))))))
  ([]
   (auto-json-decoder (lazy-load-data-json))))

(defn with-handler
  "Calls the given handler and passes it down the chain. Useful when threading a process-fn."
  [process-fn handler]
  (fn [message]
    (process-fn (handler message))))

(defn uuid
  "Generates a V4 UUID and converts it to a string."
  []
  (str (java.util.UUID/randomUUID)))
