# Changelog

## 0.3.0

### Breaking

-   Remove:

    -   `sequential/with-decoder`
    -   `parallel/with-decoder`
    -   `utils/decode-sns-encoded-json`

    Added new decoding utility higher-order functions. These take a `json-fn` which decodes a JSON string as a first argument, or fallback to clojure.data.json if only one argumetn is specified and the clojure.data.json is found on the classpath:

    -   `utils/sqs-encoded-json-decoder`
    -   `utils/sns-encoded-json-decoder`
    -   `utils/auto-json-decoder`

    The SNS functions additionally take the message attributes from the SNS payload and add them to `message-attributes` (see below).

-   Remove bundling of `clojure.data.json`
-   Changed signature of process-fn. It now receives a map:

    ```clojure
    {;; the original message, with unparsed body.
    :message message
    ;; attributes on the message, note these are different to message-attributes, e.g. ApproximateReceiveCount
    :attributes (:attributes message)
    ;; the message body itself. If this was the message from SNS it is wrapped in an SNS envelope unless RawMessageDelivery is set to true
    :message-body (:body message)
    ;; map of any message attributes set via the SQS send-message API
    :message-attributes (core/parse-message-attributes (:message-attributes message))
    ;; the sqs delete message API call pre-bound to the given message/aws config
    :delete-message #(delete-message config (:receipt-handle message))
    ;; the sqs change message visibility API call pre-bound to the given message/AWS config
    :change-message-visibility #(change-message-visibility config (:receipt-handle message) %)})
    ```

    this adds the `:change-message-visibility` function, as well as providing access to both the original message, and the message attributes.

### Added

-   Moved logging to `clojure.tools.logging`
-   Added `running` function, returned from `create-consumer`
-   Request all attributes and message attributes by default (customisable via `attribute-names` and `message-attribute-names` options on `create-consumer`)

### Upgrade guide

1. Change the signature of your `process-fn` from

    ```clojure
    (defn process-fn
        [message-body]
        ...)
    ```

    to

    ```clojure
    (defn process-fn
        [{:keys [message-body]}]
        ...)
    ```

2. Replace any usage of `queue.paralle/with-decoder` and `queue.sequential/with-decoder` with , either add a json decoder function fo choice or add `org.clojure/data.jso]` to your classpath

    ```clojure
    :process-fn (-> process
        (queue.sequential/with-decoder queue.utils/decode-sns-encoded-json)
        (queue.sequential/with-auto-delete)
        (queue.sequential/with-error-handling #(prn % "error processing messages")))
    ```

    to

    ```clojure
    :process-fn (-> process
        ;; or (queue.utilsauto-json-decoder json-fn) to use cheshire/jsonista another library
        (queue.utils/with-decoder (que.utils/auto-json-decoder))
        (queue.batch/with-auto-delete)
        (queue.batch/with-error-handling #(prn % "error processing messages")))
    ```

## 0.2.0

### Breaking

Simplifying how to create a consumer. When creating a consumer from the `sequential` namespace it is now no longer required to wrap your processing function with another call to `sequential-process`. This is also true for the `batch` and `parallel` namespaces.

## 0.1.9-SNAPSHOT

### Breaking

-   `core/create-consumer` no longer accepts a variable number of arguments, instead requires an object of options. Other namespaces remain unchanged

### Added

-   parallel processing namespace (using Claypoole)

```

```
