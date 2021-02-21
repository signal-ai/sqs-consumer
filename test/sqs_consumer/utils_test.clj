(ns sqs-consumer.utils-test
  (:require [clojure.test :refer [deftest is]]
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

(deftest auto-decode-json-message-decodes-sns-with-no-args
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001 :timestamp "2018-04-17T11:33:44.770Z"}
          :message-attributes {:Type "Orchestration.Services.Model.Pollution.PollutionMessage"
                               :AWS.SNS.MOBILE.MPNS.Type "token"}
          :sqs-consumer/metadata {:message-envelope :sns}}
         ((utils/auto-decode-json-message) (test-sns-message)))))

(deftest auto-decode-json-message-adds-timestamp-from-sns-message-if-absent
  (is (= "2020-04-17T11:33:44.770Z"
         (-> ((utils/auto-decode-json-message)
              (test-sns-message {"Timestamp" "2020-04-17T11:33:44.770Z"}))
             :message-body
             :timestamp))))

(deftest auto-decode-json-message-does-not-add-timestamp-from-sns-message-if-present
  (is (= "2017-01-12T09:33:44.770ZZ"
         (-> ((utils/auto-decode-json-message)
              (test-sns-message {"Message" "{\"timestamp\" \"2017-01-12T09:33:44.770ZZ\",\"validFrom\": \"2018-03-10T09:00:00Z\",\"validTo\": \"2018-03-11T09:00:00Z\",\"eventLevelId\": 1,\"eventTypeId\": 1,\"operatorId\": 3375001}"
                                 "Timestamp" "2020-04-17T11:33:44.770Z"}))
             :message-body
             :timestamp))))

(deftest auto-decode-json-message-decodes-sns-with-jsonista
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001 :timestamp "2018-04-17T11:33:44.770Z"}
          :message-attributes {:Type "Orchestration.Services.Model.Pollution.PollutionMessage"
                               :AWS.SNS.MOBILE.MPNS.Type "token"}
          :sqs-consumer/metadata {:message-envelope :sns}}
         ((utils/auto-decode-json-message #(jsonista/read-value % jsonista/keyword-keys-object-mapper)) (test-sns-message)))))

(deftest auto-decode-json-message-decodes-sqs-with-no-args
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001}
          :sqs-consumer/metadata {:message-envelope :sqs}}
         ((utils/auto-decode-json-message)
          "{\"validFrom\": \"2018-03-10T09:00:00Z\",\"validTo\": \"2018-03-11T09:00:00Z\",\"eventLevelId\": 1,\"eventTypeId\": 1,\"operatorId\": 3375001}"))))

(deftest auto-decode-json-message-decodes-sqs-with-jsonista
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001}
          :sqs-consumer/metadata {:message-envelope :sqs}}
         ((utils/auto-decode-json-message #(jsonista/read-value % jsonista/keyword-keys-object-mapper))
          "{\"validFrom\": \"2018-03-10T09:00:00Z\",\"validTo\": \"2018-03-11T09:00:00Z\",\"eventLevelId\": 1,\"eventTypeId\": 1,\"operatorId\": 3375001}"))))


(deftest decode-sns-encoded-json-decodes-sns-with-no-args
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001 :timestamp "2018-04-17T11:33:44.770Z"}
          :message-attributes {:Type "Orchestration.Services.Model.Pollution.PollutionMessage"
                               :AWS.SNS.MOBILE.MPNS.Type "token"}
          :sqs-consumer/metadata {:message-envelope :sns}}
         ((utils/decode-sns-encoded-json) (test-sns-message)))))


(deftest decode-sqs-encoded-json-decodes-sqs-with-no-args
  (is (= {:message-body {:validFrom "2018-03-10T09:00:00Z" :validTo "2018-03-11T09:00:00Z" :eventLevelId 1 :eventTypeId 1 :operatorId 3375001}
          :sqs-consumer/metadata {:message-envelope :sqs}}
         ((utils/decode-sqs-encoded-json)
          "{\"validFrom\": \"2018-03-10T09:00:00Z\",\"validTo\": \"2018-03-11T09:00:00Z\",\"eventLevelId\": 1,\"eventTypeId\": 1,\"operatorId\": 3375001}"))))

(deftest uuid-returns-a-v4-uuid-as-a-string
  (let [uuid (utils/uuid)]
    (is (string? uuid))
    (is (= 36 (count uuid)))
    (is (true? (clojure.core/uuid? (java.util.UUID/fromString uuid))))))
