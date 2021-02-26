(ns sqs-consumer.seq-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [amazonica.aws.sqs :as sqs]
            [greenpowermonitor.test-doubles :as td]
            [sqs-consumer.core :refer [get-queue-url]]
            [sqs-consumer.sequential :as seq])
  (:import java.io.FileNotFoundException))

(def test-queue-name "seq-test-queue")

(defn processing-function [_]
  (prn "calling function"))

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
                       {:VisibilityTimeout 300 ; sec
                        :MaximumMessageSize 65536 ; bytes
                        :MessageRetentionPeriod 1209600 ; sec
                        :ReceiveMessageWaitTimeSeconds 10})
                      (f)
                      (sqs/delete-queue
                       aws-config
                       (get-queue-url aws-config test-queue-name))))

(defn test-consumer [process]
  (seq/create-consumer :queue-name test-queue-name
                       :max-number-of-messages 5
                       :shutdown-wait-time-ms 1500
                       :wait-time-seconds 1
                       :aws-config aws-config
                       :process-fn process))

(defn just-the-body [process-fn]
  (fn [{:keys [message-body]}]
    (process-fn message-body)))

(deftest sequential-consumer-test
  (testing "can create the consumer"
    (let [{:keys [start-consumer]} (test-consumer processing-function)]
      (is (not (nil? start-consumer)))))

  (testing "can start the consumer"
    (let [{:keys [config start-consumer stop-consumer]} (test-consumer processing-function)
          consumer (future (start-consumer))]
      (is (not (nil? consumer)))
      (is (nil? (stop-consumer)))
      (Thread/sleep 2000)
      (is (true? @(:finished-shutdown config)))))

  (testing "can receive messages"
    (td/with-doubles
      :spying [processing-function]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer (-> processing-function
                                                                             just-the-body))
            _ (sqs/send-message aws-config :queue-url (get-queue-url aws-config test-queue-name) :message-body "hello world 1")
            _ (sqs/send-message aws-config :queue-url (get-queue-url aws-config test-queue-name) :message-body "hello world 2")
            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        ;; expect here that messages are processed one by one, sequentially
        (is (= 2 (-> processing-function td/calls-to count)))
        (is (= [["hello world 1"] ["hello world 2"]] (td/calls-to processing-function)))
        (is (nil? (stop-consumer)))
        (Thread/sleep 100)
        (is (true? @(:finished-shutdown config))))))

  (testing "can receive messages with auto-deleting"
    (td/with-doubles
      :spying [processing-function]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer (-> processing-function
                                                                             just-the-body
                                                                             seq/with-auto-delete))
            _ (sqs/send-message aws-config :queue-url (get-queue-url aws-config test-queue-name) :message-body "hello world 1")
            _ (sqs/send-message aws-config :queue-url (get-queue-url aws-config test-queue-name) :message-body "hello world 2")
            _ (Thread/sleep 100)
            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        ;; expect here that messages are processed one by one, sequentially
        (is (= 2 (-> processing-function td/calls-to count)))
        (is (= [["hello world 1"] ["hello world 2"]] (td/calls-to processing-function)))
        (is (nil? (stop-consumer)))
        (Thread/sleep 100)
        (is (true? @(:finished-shutdown config)))))))
