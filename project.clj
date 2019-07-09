(defproject sqs-consumer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[amazonica "0.3.139" :exclusions [com.amazonaws/aws-java-sdk]]
                 [com.amazonaws/aws-java-sdk-sqs "1.11.475"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/data.json "0.2.6"]]}})
