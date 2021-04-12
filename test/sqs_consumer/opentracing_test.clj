(ns sqs-consumer.opentracing-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [sqs-consumer.sequential :as seq]
            [sqs-consumer.opentracing :as opentracing]
            [opentracing-clj.core :as tracing]
            [opentracing-clj.propagation :as propagation])
  (:import
   [io.opentracing References]
   [io.opentracing.mock MockTracer MockSpan]))


(defn with-mock-tracer
  [f]
  (binding [tracing/*tracer* (new MockTracer)]
    (f)))

(def ^:private processing-fn-calls (atom {:args []}))
(def ^:private span-state (atom {:span nil
                                 :tags nil}))

(use-fixtures :each (fn [f]
                      (reset! processing-fn-calls {:args []})
                      (reset! span-state {:span nil
                                          :tags nil})
                      (with-mock-tracer f)))

(deftest traces-messages
  (let [message-body {:id "123"
                      :attribute {:sub-attribute true}}
        processing-function (fn  [& args]
                              (swap! processing-fn-calls #(hash-map :args (conj (:args %) args)))
                              (swap! span-state (constantly (tracing/active-span))))
        process-fn (-> processing-function
                       (opentracing/with-tracing :span-ctx))
        message {:body message-body}
        config {:visibility-timeout 123
                :queue-url "https://sqs.us-east-1.amazonaws.com/1234567890/default_development"
                :queue-name "some-queue-name"}
        initial-message (seq/extract-message config message)]

    (process-fn initial-message)
    (testing "passes through message unchanged"
      (is (= 1 (-> @processing-fn-calls :args count)) "Expected process-fn to be called once")
      (is (= 1 (-> @processing-fn-calls :args first count)) "Expected process-fn to be called with one argument")
      (is (= initial-message (-> @processing-fn-calls :args first first))))

    (testing "sets span, tags and operation name correctly"
      (is (instance? MockSpan  @span-state))
      (is (= {"component" "signal-ai/sqs-consumer"
              "peer.service" "sqs"
              "span.kind" "consumer"
              "peer.address" (:queue-url config)} (.tags @span-state)))
      (is (= "queue-some-queue-name-message-recieved" (.operationName @span-state)))
      (is (= 1 (count (.finishedSpans tracing/*tracer*)))))))

(deftest sets-trace-error-on-process-error
  (let [message-body {:id "123"
                      :attribute {:sub-attribute true}}
        given-error (ex-info "Processing-error" {})
        processing-function (fn  [& args]
                              (swap! processing-fn-calls #(hash-map :args (conj (:args %) args)))
                              (swap! span-state (constantly (tracing/active-span)))
                              (throw given-error))
        process-fn (-> processing-function
                       (opentracing/with-tracing :span-ctx))
        message {:body message-body}
        config {:visibility-timeout 123
                :queue-url "https://sqs.eu-west-1.amazonaws.com/1234567800/other_development"
                :queue-name "other-queue-name"}
        initial-message (seq/extract-message config message)]

    (try
      (process-fn initial-message)
      (catch Throwable e
        (is (= given-error e))))
    (testing "sets span, tags and operation name correctly, with error"
      (is (instance? MockSpan  @span-state))
      (is (= {"component" "signal-ai/sqs-consumer"
              "peer.service" "sqs"
              "span.kind" "consumer"
              "peer.address" (:queue-url config)
              "error" true} (.tags @span-state)))
      (is (= "queue-other-queue-name-message-recieved" (.operationName @span-state)))
      (is (= 1 (count (.finishedSpans tracing/*tracer*)))))))

(deftest propagates-headers-from-attributes
  (let [original-span (atom nil)
        propagation-headers (tracing/with-span [s {:name "original-span"}]
                              (reset! original-span s)
                              (propagation/inject :text))
        trace-attribute :span-ctx

        message-body {:id "123"
                      :attribute {:sub-attribute true}}
        given-error (ex-info "Processing-error" {})
        processing-function (fn  [& args]
                              (swap! processing-fn-calls #(hash-map :args (conj (:args %) args)))
                              (swap! span-state (constantly (tracing/active-span))))
        process-fn (-> processing-function
                       (opentracing/with-tracing trace-attribute))
        message {:body message-body}
        config {:queue-url "https://sqs.eu-west-1.amazonaws.com/1234567800/other_development"
                :queue-name "other-queue-name"}
        initial-message (assoc (seq/extract-message config message) :message-attributes {trace-attribute propagation-headers})]

    (try
      (process-fn initial-message)
      (catch Throwable e
        (is (= given-error e))))
    (testing "sets child context correctly"
      (is (instance? MockSpan  @span-state))
      (is (= {"component" "signal-ai/sqs-consumer"
              "peer.service" "sqs"
              "span.kind" "consumer"
              "peer.address" (:queue-url config)} (.tags @span-state)))

      (let [original-span-trace-id (.toTraceId (.context @original-span))
            child-span-references  (.references @span-state)]
        (is (= 1 (count child-span-references)))
        (is (not (nil? original-span-trace-id)))
        (is (= [References/CHILD_OF original-span-trace-id]
               [(.getReferenceType (first child-span-references)) (.toTraceId (.getContext (first child-span-references)))]))))))
