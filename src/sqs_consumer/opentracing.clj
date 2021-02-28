(ns sqs-consumer.opentracing
  (:require
   [sqs-consumer.core :as core]
   [opentracing-clj.core :as tracing]
   [opentracing-clj.propagation :as propagation])
  (:import [io.opentracing.tag Tags]))

(defn with-tracing
  "Add a trace to the queue. Propagates context from the :span-ctx attribute if it exists.
   
   If the queue messages are from SNS with RawMessageDelivery set to true should be placed after message decoding (e.g. sqs-consumer.utils/decode-sns-encoded-json)
   so any span context can be propagated from message attributes.
   Otherwise can be placed earlier."
  [process-fn span-context-attribute-name]
  (fn [message]
    (let [ctx (when-let [carrier (-> message :message-attributes span-context-attribute-name)] (propagation/extract carrier :text))]
      (tracing/with-span [s
                          (cond-> {:name (format "queue-%s-message-recieved" (-> message ::core/config :queue-name))
                                   :tags {(.getKey Tags/COMPONENT) "signal-ai/sqs-consumer"
                                          (.getKey Tags/SPAN_KIND) Tags/SPAN_KIND_CONSUMER
                                          (.getKey Tags/PEER_SERVICE) "sqs"
                                          "peer.address" (-> message ::core/config :queue-url)}}
                            ctx (assoc :child-of ctx))]
        (try
          (process-fn message)
          (catch Throwable e
            (tracing/set-tags {Tags/ERROR true})
            (tracing/log {:event "error"
                          :error.object e})
            (throw e)))))))
