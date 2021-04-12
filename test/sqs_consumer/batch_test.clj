(ns sqs-consumer.batch-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [sqs-consumer.batch :as batch]
            [amazonica.aws.sqs :as sqs]
            [greenpowermonitor.test-doubles :as td]
            [sqs-consumer.core :refer [get-queue-url]]
            [sqs-consumer.localstack :as localstack]))

(def test-queue-name "batch-test-queue")

(defn processing-function [_]
  (prn "calling function")
  true)

(defn test-error-handler [e]
  (prn e))

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

(defn test-consumer [process]
  (batch/create-consumer :queue-name test-queue-name
                         :max-number-of-messages 10
                         :shutdown-wait-time-ms 1500
                         :wait-time-seconds 1
                         :aws-config localstack/aws-config
                         :process-fn process))

(deftest batch-consumer-test
  (testing "can receive a batch"
    (td/with-doubles
      :spying [processing-function
               test-error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer (-> processing-function
                                                                             (batch/with-error-handling test-error-handler)))
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world")
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world")
            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 1000)
        (is (nil? (stop-consumer)))
        (is (= 1 (-> processing-function td/calls-to count)))
        (is (= 0 (-> test-error-handler td/calls-to count)))
        (is (true? @(:finished-shutdown config))))))
  (testing "can receive a batch with auto deleting"
    (td/with-doubles
      :spying [processing-function
               test-error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer
                                                           (-> processing-function
                                                               batch/with-auto-delete
                                                               (batch/with-error-handling test-error-handler)))
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world 1")
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world 2")

            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        (is (= 1 (-> processing-function td/calls-to count)))
        (is (= 0 (-> test-error-handler td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config))))))
  (testing "can receive a batch with decoding"
    (td/with-doubles
      :spying [processing-function
               test-error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer
                                                           (-> processing-function
                                                               (batch/with-decoder #(hash-map :message-body %))
                                                               batch/with-auto-delete
                                                               (batch/with-error-handling test-error-handler)))
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world 1")
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world 2")

            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        (is (= 1 (-> processing-function td/calls-to count)))
        (is (= [[["hello world 1" "hello world 2"]]] (td/calls-to processing-function)))
        (is (= 0 (-> test-error-handler td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config))))))
  (testing "will call the error handler when the processor errors"
    (td/with-doubles
      :spying [processing-function
               test-error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer
                                                           (-> (fn [_] (throw (new Exception "testing error handling")))
                                                               (batch/with-decoder #(hash-map :message-body %))
                                                               batch/with-auto-delete
                                                               (batch/with-error-handling test-error-handler)))
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world")
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world")

            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        (is (= 0 (-> processing-function td/calls-to count)))
        (is (= 1 (-> test-error-handler td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config))))))
  (testing "will call the error handler when the decoder errors"
    (td/with-doubles
      :spying [processing-function
               test-error-handler]
      (let [{:keys [config start-consumer stop-consumer]} (test-consumer
                                                           (-> processing-function
                                                               (batch/with-decoder (fn [_] (throw (new Exception "unable to decode message"))))
                                                               batch/with-auto-delete
                                                               (batch/with-error-handling test-error-handler)))
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world")
            _ (sqs/send-message localstack/aws-config
                                :queue-url (get-queue-url localstack/aws-config test-queue-name)
                                :message-body "hello world")

            consumer (future (start-consumer))]
        (is (not (nil? consumer)))
        (Thread/sleep 100)
        (is (= 0 (-> processing-function td/calls-to count)))
        (is (= 1 (-> test-error-handler td/calls-to count)))
        (is (nil? (stop-consumer)))
        (is (true? @(:finished-shutdown config)))))))
