# SQS Consumer
[![Clojars Project](https://img.shields.io/clojars/v/signal-ai/sqs-consumer.svg)](https://clojars.org/signal-ai/sqs-consumer)

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
Both the `utils` and `parallel` have transitive dependencies that are *not* bundled with this library. If you don't need them, don't declare the dependencies. Be sure to include these dependencies when you do use them:
```clj
[org.clojure/data.json "0.2.6"] ;; required by utils
[com.climate/claypoole "1.1.4"] ;; required by parallel
```

Some common usage patterns, the usage should be fairly similar:

### Sequential processing

```clj
(require [sqs-consumer.sequential :as queue.sequential]
         [sqs-consumer.utils :as queue.utils])

(defn process [message-body]
  (prn message-body))

(defn create-queue-consumer []
  (queue.sequential/create-consumer :queue-url "sqs-queue-name"
                                    :max-number-of-messages 5
                                    :shutdown-wait-time-ms 2000
                                    :process-fn (-> process
                                                    (queue.sequential/with-message-decoder queue.utils/decode-sns-encoded-json)
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
                                               (queue.batch/with-message-decoder queue.utils/decode-sns-encoded-json)
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

(defn process [message-batch]
  (prn (count message-batch))

(defn create-queue-consumer []
  (queue.parallel/create-consumer :queue-url "sqs-queue-name"
                                  :max-number-of-messages 10
                                  :threadpool-size 3 ;; defaults to 10. Should be smaller than the number of messages that are dequeued from SQS. More will just mean un-used threads
                                  :shutdown-wait-time-ms 2000
                                  :process-fn (-> process
                                                  (queue.parallel/with-message-decoder queue.utils/decode-sns-encoded-json)
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


### Queue URL vs Queue Name

If you pass `queue-url` then `queue-name` will never be used. If you only pass `queue-name` then `queue-url` will be looked up; AWS will throw an exception if a Queue with that name cannot be found. If neither are passed then an `IllegalArgumentException` will be thrown.

## TODO
 - [ ] deps.edn?
 - [ ] Don't hard code visibility timeout
 - [ ] Support being able to extend message timeout
 - [ ] Better documentation
 - [ ] Choose a license?
 - [ ] Tests are a bit flaky - sometimes due to timing they fail
 - [ ] metadata from SQS and SNS is lost during the deserialisation, maybe some of that is needed?
 - [x] Should we be using `pmap` or `map` across message?
 - [ ] Can we lose the dependency on `data.json`?


## Local development

Testing is done inside docker so no Clojure or AWS depencies are required.
Required tools:
 - Docker
 - Docker Compose
 
### Running the tests
```
docker-compose up -d
docker-compose build && docker-compose run --rm sqs_consumer lein test
```

## License

Copyright © 2019 Signal AI

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
