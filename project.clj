(defproject sqs-consumer "0.1.6-SNAPSHOT"
  :description "Another SQS Library"
  :url "https://github.com/signal-ai/sqs-consumer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[amazonica "0.3.139" :exclusions [com.amazonaws/aws-java-sdk]]
                 [com.amazonaws/aws-java-sdk-sqs "1.11.475"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/data.json "0.2.6"]
                                  [greenpowermonitor/test-doubles "0.1.2"]]}}
   :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password}]
                         ["snapshots" {:url "https://clojars.org/repo"
                                       :username :env/clojars_username
                                       :password :env/clojars_password}]])
