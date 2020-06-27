(ns sqs-consumer.core-test
  (:require [clojure.test :refer :all]
            [sqs-consumer.core :refer :all]
            [amazonica.aws.sqs :as sqs]
            [greenpowermonitor.test-doubles :as td])
  (:import java.io.FileNotFoundException))

(def test-queue-name "test-queue")

(defn processing-function [_]
  (prn "calling function")
  (throw (Exception. "Not implemented")))

(def aws-config {:endpoint "http://localstack:4566"
                 :client-config {}})

(defn wait-for-localstack []
  (try
    (slurp "http://localstack:8080/health")
    (prn "localstack up")
    (catch FileNotFoundException _
      (prn "waiting for localstack")
      (Thread/sleep 500)
      (wait-for-localstack))))

(use-fixtures :once (fn [f]
                      (wait-for-localstack)
                      (sqs/create-queue
                       aws-config
                       :queue-name test-queue-name
                       :attributes
                       {:VisibilityTimeout 30 ; sec
                        :MaximumMessageSize 65536 ; bytes
                        :MessageRetentionPeriod 1209600 ; sec
                        :ReceiveMessageWaitTimeSeconds 10})
                      (f)
                      (sqs/delete-queue
                       aws-config
                       (get-queue-url aws-config test-queue-name))))

(defn test-consumer []
  (create-consumer :queue-name test-queue-name
                   :max-number-of-messages 1
                   :shutdown-wait-time-ms 1500
                   :wait-time-seconds 1
                   :aws-config aws-config
                   :process-fn processing-function))

(deftest basic-consumer-test
  (testing "can create the consumer"
    (let [{:keys [start-consumer stop-consumer]} (test-consumer)]
      (is (not (nil? start-consumer)))))
  (testing "can start the consumer"
    (let [{:keys [config start-consumer stop-consumer]} (test-consumer)
          consumer (future (start-consumer))]
      (is (not (nil? consumer)))
      (is (nil? (stop-consumer)))
      (is (true? @(:finished-shutdown config)))))
  (testing "can receive a message"
    (td/with-doubles
      :spying [processing-function]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer)
            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (is (not (nil? (sqs/send-message aws-config :queue-url (get-queue-url aws-config test-queue-name) :message-body "hello world"))))
        (Thread/sleep 100)
        (is (= 1 (-> processing-function td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config)))
        ))))
