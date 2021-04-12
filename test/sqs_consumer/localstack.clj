(ns sqs-consumer.localstack
  (:require [clojure.tools.logging :as log]))

(def ^:private localstack-host
  (or (System/getenv "LOCALSTACK_HOST")
      "localhost"))

(def aws-config {:access-key "localstack"
                 :secret-key "localstack"
                 :endpoint (format "http://%s:4566" localstack-host)
                 :client-config {}})

(def ^:private localstack-retry-count (atom 0))

(defn wait-for-localstack []
  (let [localstack-url (format "http://%s:4566/health" localstack-host)]
    (log/infof "looking up localstack at %s, retry no: %s" localstack-url  @localstack-retry-count)
    (try
      (slurp localstack-url)
      (log/infof "localstack ready at %s" localstack-url)
      (catch Exception e
        (if (>= @localstack-retry-count 10)
          (do
            (log/fatalf "retry limit reached waiting for localstack at %s, %s" localstack-url)
            (System/exit 1))
          (do
            (swap! localstack-retry-count inc)
            (log/infof "waiting for localstack at %s, %s" localstack-url (.getMessage e))
            (Thread/sleep 500)
            (wait-for-localstack)))))))
