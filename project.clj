(defproject com.signal-ai/sqs-consumer "0.3.0"
  :description "Another SQS Library"
  :url "https://github.com/signal-ai/sqs-consumer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[amazonica "0.3.153" :exclusions [com.fasterxml.jackson.core/jackson-databind
                                                   com.fasterxml.jackson.core/jackson-core
                                                   commons-logging
                                                   com.amazonaws/aws-java-sdk]]
                 [com.amazonaws/aws-java-sdk-sqs "1.11.964"]

                 [org.clojure/tools.logging "1.1.0"]
                 [com.clojure-goes-fast/lazy-require "0.1.1"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.2"]

                                  [org.clojure/data.json "1.0.0"]
                                  [opentracing-clj "0.2.2"]
                                  [io.opentracing/opentracing-mock "0.33.0"]
                                  [metosin/jsonista "0.3.1"]
                                  [com.climate/claypoole "1.1.4"]

                                  ;; testing deps
                                  [greenpowermonitor/test-doubles "0.1.2"]

                                  [lambdaisland/kaocha "1.0.732"]
                                  [lambdaisland/kaocha-junit-xml "0.0.76"]
                                  [lambdaisland/kaocha-cloverage "1.0.75"]]}
             :test {:env {:localstack-host "localhost"}}}
  :aliases {"test" ["with-profile" "dev,test" "run" "-m" "kaocha.runner"]}
  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_username
                                     :password :env/clojars_token
                                     :sign-releases false}]
                        ["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_token}]])
