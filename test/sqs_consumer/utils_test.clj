(ns sqs-consumer.utils-test
  (:require [clojure.test :refer [deftest is]]
            [greenpowermonitor.test-doubles :as td]
            [sqs-consumer.core :as core]
            [sqs-consumer.sequential :as seq]
            [sqs-consumer.utils :as utils]
            [jsonista.core :as jsonista]))

(defn- test-sns-message
  ([opts] (jsonista/write-value-as-string
           (merge
            {"Type" "Notification"
             "MessageId" "78d5bc6f-142c-5060-a75c-ef29b774ec66"
             "TopicArn" "arn:aws:sns:eu-west-2:xxx:pollution-event"
             "Message" "{\"validFrom\": \"2018-03-10T09:00:00Z\",\"validTo\": \"2018-03-11T09:00:00Z\",\"eventLevelId\": 1,\"eventTypeId\": 1,\"operatorId\": 3375001}"
             "Timestamp" "2018-04-17T11:33:44.770Z"
             "SignatureVersion" "1"
             "Signature" "xxx=="
             "SigningCertURL" "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-xxx.pem"
             "MessageAttributes" {"Type" {"Type" "String"
                                          "Value" "Orchestration.Services.Model.Pollution.PollutionMessage"}
                                  "AWS.SNS.MOBILE.MPNS.Type" {"Type" "String"
                                                              "Value" "token"}}} opts)))
  ([]
   (test-sns-message {})))

(defn- wrapped-message [message-body]
  {:message-body message-body})

(deftest auto-decode-json-message-decodes-sns-no-args
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001 :timestamp "2018-04-17T11:33:44.770Z"}
          :message-attributes {:Type "Orchestration.Services.Model.Pollution.PollutionMessage"
                               :AWS.SNS.MOBILE.MPNS.Type "token"}
          ::core/metadata {:message-envelope :sns}}
         ((utils/auto-json-decoder) (wrapped-message (test-sns-message))))))

(deftest auto-decode-json-message-adds-timestamp-from-sns-message-if-absent
  (is (= "2020-04-17T11:33:44.770Z"
         (-> ((utils/auto-json-decoder)
              (wrapped-message (test-sns-message {"Timestamp" "2020-04-17T11:33:44.770Z"})))
             :message-body
             :timestamp))))

(deftest auto-decode-json-message-does-not-add-timestamp-from-sns-message-if-present
  (is (= "2017-01-12T09:33:44.770ZZ"
         (-> ((utils/auto-json-decoder)
              (wrapped-message (test-sns-message {"Message" "{\"timestamp\" \"2017-01-12T09:33:44.770ZZ\",\"validFrom\": \"2018-03-10T09:00:00Z\",\"validTo\": \"2018-03-11T09:00:00Z\",\"eventLevelId\": 1,\"eventTypeId\": 1,\"operatorId\": 3375001}"
                                                  "Timestamp" "2020-04-17T11:33:44.770Z"})))
             :message-body
             :timestamp))))

(deftest auto-decode-json-message-decodes-sns-jsonista
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001 :timestamp "2018-04-17T11:33:44.770Z"}
          :message-attributes {:Type "Orchestration.Services.Model.Pollution.PollutionMessage"
                               :AWS.SNS.MOBILE.MPNS.Type "token"}
          ::core/metadata {:message-envelope :sns}}
         ((utils/auto-json-decoder #(jsonista/read-value % jsonista/keyword-keys-object-mapper)) (wrapped-message (test-sns-message))))))

(deftest auto-decode-json-message-decodes-sqs-no-args
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001}
          ::core/metadata {:message-envelope :sqs}}
         ((utils/auto-json-decoder)
          (wrapped-message "{\"validFrom\": \"2018-03-10T09:00:00Z\",\"validTo\": \"2018-03-11T09:00:00Z\",\"eventLevelId\": 1,\"eventTypeId\": 1,\"operatorId\": 3375001}")))))

(deftest auto-decode-json-message-decodes-sqs-jsonista
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001}
          ::core/metadata {:message-envelope :sqs}}
         ((utils/auto-json-decoder #(jsonista/read-value % jsonista/keyword-keys-object-mapper))
          (wrapped-message "{\"validFrom\": \"2018-03-10T09:00:00Z\",\"validTo\": \"2018-03-11T09:00:00Z\",\"eventLevelId\": 1,\"eventTypeId\": 1,\"operatorId\": 3375001}")))))


(deftest sns-encoded-json-decoder-decodes-sns-no-args
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001 :timestamp "2018-04-17T11:33:44.770Z"}
          :message-attributes {:Type "Orchestration.Services.Model.Pollution.PollutionMessage"
                               :AWS.SNS.MOBILE.MPNS.Type "token"}
          ::core/metadata {:message-envelope :sns}}
         ((utils/sns-encoded-json-decoder) (wrapped-message (test-sns-message))))))


(deftest sqs-encoded-json-decoder-decodes-sqs-no-args
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001}
          ::core/metadata {:message-envelope :sqs}}
         ((utils/sqs-encoded-json-decoder)
          (wrapped-message "{\"validFrom\": \"2018-03-10T09:00:00Z\",\"validTo\": \"2018-03-11T09:00:00Z\",\"eventLevelId\": 1,\"eventTypeId\": 1,\"operatorId\": 3375001}")))))

(defn processing-function [_])

(deftest with-decoder-passes-through-values-correctly
  (td/with-doubles
    :spying [processing-function]
    (let [message-body {:id "123"
                        :attribute {:sub-attribute true}}
          process-fn (-> processing-function
                         (utils/with-decoder (utils/auto-json-decoder)))
          message {:body (jsonista/write-value-as-string message-body)}
          config {:visibility-timeout 123}]

      (process-fn (seq/extract-message config message))
      (is (= 1 (-> processing-function td/calls-to count)) "Expected process-fn to be called once")
      (is (= 1 (-> processing-function td/calls-to first count)) "Expected process-fn to be called with one argument")
      (is (= #{:message
               :attributes
               :message-body
               :message-attributes
               :delete-message
               :change-message-visibility
               :sqs-consumer.core/metadata
               :sqs-consumer.core/config} (-> processing-function td/calls-to first first keys set)))
      (is (= config (-> processing-function td/calls-to first first :sqs-consumer.core/config)))
      (is (= message-body (-> processing-function td/calls-to first first :message-body))))))

(deftest uuid-returns-a-v4-uuid-as-a-string
  (let [uuid (utils/uuid)]
    (is (string? uuid))
    (is (= 36 (count uuid)))
    (is (true? (clojure.core/uuid? (java.util.UUID/fromString uuid))))))
