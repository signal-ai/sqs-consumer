(ns sqs-consumer.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [amazonica.aws.sqs :as sqs]
            [greenpowermonitor.test-doubles :as td]
            [sqs-consumer.core :refer [create-consumer get-queue-url]]
            [sqs-consumer.localstack :as localstack]))

(def test-queue-name "test-queue")

(defn processing-function [_]
  (prn "calling function"))

(use-fixtures :once (fn [f]
                      (localstack/wait-for-localstack)
                      (sqs/create-queue
                       localstack/aws-config
                       :queue-name test-queue-name
                       :attributes
                       {:VisibilityTimeout 30 ; sec
                        :MaximumMessageSize 65536 ; bytes
                        :MessageRetentionPeriod 1209600 ; sec
                        :ReceiveMessageWaitTimeSeconds 10})
                      (f)
                      (sqs/delete-queue
                       localstack/aws-config
                       (get-queue-url localstack/aws-config test-queue-name))))

(defn test-consumer
  ([process-fn]
   (create-consumer {:queue-name test-queue-name
                     :max-number-of-messages 5
                     :shutdown-wait-time-ms 1500
                     :wait-time-seconds 1
                     :aws-config localstack/aws-config
                     :process-fn process-fn}))
  ([]
   (test-consumer processing-function)))

(deftest basic-consumer-test
  (testing "can create the consumer"
    (let [{:keys [start-consumer]} (test-consumer)]
      (is (not (nil? start-consumer)))))

  (testing "can start the consumer"
    (let [{:keys [config start-consumer stop-consumer]} (test-consumer)
          consumer (future (start-consumer))]
      (is (not (nil? consumer)))
      (is (nil? (stop-consumer)))
      (Thread/sleep 2000)
      (is (true? @(:finished-shutdown config)))))

  (testing "can receive a message"
    (td/with-doubles
      :spying [processing-function]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer)
            _ (sqs/send-message localstack/aws-config :queue-url (get-queue-url localstack/aws-config test-queue-name) :message-body "hello world")
            _ (sqs/send-message localstack/aws-config :queue-url (get-queue-url localstack/aws-config test-queue-name) :message-body "hello world")
            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        ;; expect here that we get a batch of messages as we don't fan out
        (is (= 1 (-> processing-function td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config)))))))
