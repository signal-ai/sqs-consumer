(ns sqs-consumer.batch-test
  (:require [clojure.test :refer :all]
            [sqs-consumer.batch :as batch]
            [sqs-consumer.core :refer :all]
            [amazonica.aws.sqs :as sqs]
            [greenpowermonitor.test-doubles :as td])
  (:import java.io.FileNotFoundException))

(def test-queue-name "test-queue")

(defn processing-function [_]
  (prn "calling function")
  true)

(defn error-handler [e]
  (prn e))

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

(defn test-consumer [process]
  (create-consumer :queue-name test-queue-name
                   :max-number-of-messages 10
                   :shutdown-wait-time-ms 1500
                   :wait-time-seconds 1
                   :aws-config aws-config
                   :process-fn process))

(deftest batch-consumer-test
  (testing "can receive a batch"
    (td/with-doubles
      :spying [processing-function
               error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer (-> processing-function
                                                                             (batch/with-error-handling error-handler)
                                                                             batch/batch-process))
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world")
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world")
            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 1000)
        (is (nil? (stop-consumer)))
        (is (= 1 (-> processing-function td/calls-to count)))
        (is (= 0 (-> error-handler td/calls-to count)))
        (is (true? @(:finished-shutdown config)))
        )))
  (testing "can receive a batch with auto deleting"
    (td/with-doubles
      :spying [processing-function
               error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer
                                                           (-> processing-function
                                                               batch/with-auto-delete
                                                               (batch/with-error-handling error-handler)
                                                               batch/batch-process))
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world 1")
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world 2")

            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        (is (= 1 (-> processing-function td/calls-to count)))
        (is (= 0 (-> error-handler td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config)))
        )))
  (testing "can receive a batch with decoding"
    (td/with-doubles
      :spying [processing-function
               error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer
                                                           (-> processing-function
                                                               (batch/with-decoder identity)
                                                               batch/with-auto-delete
                                                               (batch/with-error-handling error-handler)
                                                               batch/batch-process))
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world 1")
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world 2")

            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        (is (= 1 (-> processing-function td/calls-to count)))
        (is (= [[["hello world 1" "hello world 2"]]] (td/calls-to processing-function)))
        (is (= 0 (-> error-handler td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config)))
        )))
  (testing "will call the error handler when the processor errors"
    (td/with-doubles
      :spying [processing-function
               error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer
                                                           (-> (fn [batch] (throw (new Exception "testing error handling")))
                                                               (batch/with-decoder identity)
                                                               batch/with-auto-delete
                                                               (batch/with-error-handling error-handler)
                                                               batch/batch-process))
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world")
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world")

            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        (is (= 0 (-> processing-function td/calls-to count)))
        (is (= 1 (-> error-handler td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config)))
        )))
  (testing "will call the error handler when the decoder errors"
    (td/with-doubles
      :spying [processing-function
               error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer
                                                           (-> processing-function
                                                               (batch/with-decoder (fn [_] (throw (new Exception "unable to decode message"))))
                                                               batch/with-auto-delete
                                                               (batch/with-error-handling error-handler)
                                                               batch/batch-process))
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world")
            _ (sqs/send-message aws-config
                                :queue-url (get-queue-url aws-config test-queue-name)
                                :message-body "hello world")

            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        (is (= 0 (-> processing-function td/calls-to count)))
        (is (= 1 (-> error-handler td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config)))
        ))))
