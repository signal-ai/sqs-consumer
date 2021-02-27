FROM clojure:openjdk-11-lein-2.9.5-slim-buster

WORKDIR /srv

COPY project.clj .
RUN lein deps
COPY src test ./
