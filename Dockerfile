FROM clojure:lein-2.9.0 AS deps
WORKDIR /srv
ADD project.clj .
RUN lein deps
ADD src src
ADD test test

FROM clojure:lein-2.9.0 AS builder
WORKDIR /srv
COPY --from=deps /srv .
COPY --from=deps /root/.m2 /root/.m2
RUN lein uberjar