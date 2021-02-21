# SQS Consumer

[![Clojars Project](https://img.shields.io/clojars/v/com.signal-ai/sqs-consumer.svg)](https://clojars.org/com.signal-ai/sqs-consumer)

## Rationale

There are a few alternative SQS queuing libraries. None of them focus on creating components that can easily be started and stopped gracefully. This library focusses on solving just that problem.

Libraries should tie you into using the fewest number of other libraries as possible. For example this library will never tie you into an implementation of components such as Stuart Sierra's components or Mount components. This library should work with what ever choice you make. The same is true for async code, we don't want to tie you into core.async.

There are a number of dependencies for the `utils` ns, these should be dev dependencies and only required if you optionally want to use any of the middleware provided in that ns.

## Usage

`sequential.clj` is a wrapper and middlewares for processing messages sequentially

`batch.clj` is a wrapper and middlewares for processing messages as a single batch

`parallel.clj` is a wrapper and middlewares for processing messages in parallel - limited by the number of messages that are dequed at a time.

`utils.clj` contains ring like middleware to take care of some of the common processing you might want to do on a message

### Dependencies

Both the `utils` and `parallel` have transitive dependencies that are _not_ bundled with this library. If you don't need them, don't declare the dependencies. Be sure to include these dependencies when you do use them:

```clj
[org.clojure/data.json "0.2.6"] ;; required by no-args calls to json decoders in utils
[com.climate/claypoole "1.1.4"] ;; required by parallel
```

Some common usage patterns, the usage should be fairly similar:

### Sequential processing

```clj
(require [sqs-consumer.sequential :as queue.sequential]
         [sqs-consumer.utils :as queue.utils])

;; called with each individual message, see core/parse-message-attributes for the provided arguments
(defn process [{:keys [message-body]}]
  (prn message-body))

(defn create-queue-consumer []
  (queue.sequential/create-consumer :queue-url "sqs-queue-name"
                                    :max-number-of-messages 5
                                    :shutdown-wait-time-ms 2000
                                    :process-fn (-> process
                                                    (queue.utils/with-auto-message-decoder (queue.utils/with-auto-message-decoder))
                                                    ;; optional. If not included, a zero-arg function delete-message is provided to process-fn
                                                    (queue.sequential/with-auto-delete)
                                                    (queue.sequential/with-error-handling #(prn % "error processing message")))))

(let [{:keys [start-consumer
              stop-consumer]} (create-queue-consumer)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (log/info "shutting down")
                                 (stop-consumer))))
    (log/info "listening for messages...")
    (start-consumer))
```

### Batch processing

```clj
(require [sqs-consumer.batch :as queue.batch]
         [sqs-consumer.utils :as queue.utils])

(defn process [message-batch]
  (prn (count message-batch))

(defn create-queue-consumer []
  (queue.batch/create-consumer :queue-url "sqs-queue-name"
                               :max-number-of-messages 10 ;; this effectively becomes the maximum batch size
                               :shutdown-wait-time-ms 2000
                               :process-fn (-> process
                                               (queue.utils/with-handler (queue.utils/with-auto-message-decoder))
                                               (queue.batch/with-auto-delete)
                                               (queue.batch/with-error-handling #(prn % "error processing messages")))))

(let [{:keys [start-consumer
              stop-consumer]} (create-queue-consumer)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (log/info "shutting down")
                                 (stop-consumer))))
    (log/info "listening for messages...")
    (start-consumer))
```

### Parallel processing

Under the hood messages here are processed using Claypoole's `upmap` which is un-ordered and in parallel. If you're using a FIFO queue where order is guaranteed, this will likely break that guarantee.

```clj
(require [sqs-consumer.parallel :as queue.parallel]
         [sqs-consumer.utils :as queue.utils])

;; called with each individual message
(defn process [{:keys [message-body]}]
  (prn (count {:keys [message-body]}))

(defn create-queue-consumer []
  (queue.parallel/create-consumer :queue-url "sqs-queue-name"
                                  :max-number-of-messages 10
                                  :threadpool-size 3 ;; defaults to 10. Should be smaller than the number of messages that are dequeued from SQS. More will just mean un-used threads
                                  :shutdown-wait-time-ms 2000
                                  :process-fn (-> process
                                                  (queue.utils/with-handler (queue.utils/with-auto-message-decoder))
                                                  ;; optional. If not included, a zero-arg function delete-message is provided to process-fn
                                                  (queue.parallel/with-auto-delete)
                                                  (queue.parallel/with-error-handling #(prn % "error processing messages")))))

(let [{:keys [start-consumer
              stop-consumer]} (create-queue-consumer)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (log/info "shutting down")
                                 (stop-consumer))))
    (log/info "listening for messages...")
    (start-consumer))
```

### Using a different JSON decoder

The higher-order utility functions

-   `(queue.utils/with-auto-json-decoder)`
-   `(queue.utils/with-sns-encoded-json-decoder)`
-   `(queue.utils/with-sqs-encoded-json-decoder)`

all take a `json-fn` argument which can be used to customise the JSON decoding.

If they're called with zero-arguments, `clojure.data.json` is used (this is then required to be available on your classpath).

To use [jsonista](https://github.com/metosin/jsonista), construct the decoder with

```clojure
(require '[jsonista.core :as j])

(queue.utils/with-handler (queue.utils/with-auto-message-decoder #(j/read-value % j/keyword-keys-object-mapper))))
```

To use [cheshire](https://github.com/dakrone/cheshire):

```clojure
(require '[cheshire.core :as json])

(queue.utils/with-handler (queue.utils/with-auto-message-decoder #(json/parse-string % true)))
```

### Queue URL vs Queue Name

If you pass `queue-url` then `queue-name` will never be used. If you only pass `queue-name` then `queue-url` will be looked up; AWS will throw an exception if a Queue with that name cannot be found. If neither are passed then an `IllegalArgumentException` will be thrown.

### Tracing

To add tracing add `sqs-consumer.opentracing/with-tracing` to your `process-fn`. This requires the [opentracing-clj](https://github.com/alvinfrancis/opentracing-clj) library on your classpath (this is not included).

This function attempts to extract existing span context from upstream services. This context will be pulled from `:message-attributes` using the given key, assuming the `text` opentracing carrier format.

If using an SNS message without `RawMessageDelivery` set to `true`, it must be placed _after_ the decoder, else the span context from the upstream service will not have yet been pulled from the message attributes.

```clojure
(require [sqs-consumer.parallel :as queue.parallel]
         [sqs-consumer.opentracing :as queue.opentracing]
         [sqs-consumer.utils :as queue.utils])

:process-fn (-> process
  (queue.opentracing/with-tracing :span-ctx) ;; replace span-ctx with the name of the attribute you propagate traces through on an SNS or SQS message attribute
  (queue.parallel/with-decoder (queue.utils/auto-decode-json-message))
  (queue.parallel/with-error-handling #(prn % "error processing messages")))
```

If using SNS with `RawMessageDelivery` set to `true` or raw SQS messages, `with-tracing` can be placed earlier in the process-fn order, i.e. before the decoder. This means errors in decoders will still be traced.

```clojure
:process-fn (-> process
  (queue.parallel/with-decoder (queue.utils/decode-sqs-encoded-json))
  (queue.opentracing/with-tracing :span-ctx)
  (queue.parallel/with-error-handling #(prn % "error processing messages")))
```

## TODO

-   [ ] deps.edn?
-   [ ] Don't hard code visibility timeout
-   [ ] Support being able to extend message timeout
-   [ ] Better documentation
-   [ ] Choose a license?
-   [ ] Tests are a bit flaky - sometimes due to timing they fail

## Local development

Testing is done inside docker so no Clojure or AWS depencies are required.
Required tools:

-   Docker
-   Docker Compose

### Running the tests

```shell
docker-compose up -d
docker-compose build && docker-compose run --rm sqs_consumer lein test
```

## License

Copyright © 2020 Signal AI

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
