(defproject com.relaynetwork/kinematic "1.0.2"
  :description "Dynamic Web Development with Clojure"
  :dev-dependencies [[swank-clojure                        "1.4.2"]]
  :local-repo-classpath true
  :plugins [[s3-wagon-private "1.1.1"]]
  :repositories {"releases" "s3p://relay-maven-repo/releases/"
                 "snapshots" "s3p://relay-maven-repo/snapshots/"}
  :dependencies     [[org.clojure/clojure                  "1.3.0"]
                     [org.clojure/data.json                "0.1.2"]
                     [org.clojure/tools.namespace          "0.1.0"]
                     [ring/ring-core                       "1.1.0"]
                     [ring/ring-jetty-adapter              "1.1.0"]
                     [org.clojars.kyleburton/clj-etl-utils "1.3.4"]])
