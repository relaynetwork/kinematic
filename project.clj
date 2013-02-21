(defproject com.relaynetwork/kinematic "2.0.0-SNAPSHOT"
  :description          "Dynamic Web Development with Clojure"
  :dev-dependencies     [[swank-clojure                        "1.4.2"]]
  :local-repo-classpath true
  :lein-release     {:deploy-via :clojars :scm :git}
  :dependencies     [[org.clojure/tools.namespace          "0.1.0"]
                     [ring/ring-core                       "1.1.8"]
                     [ring/ring-jetty-adapter              "1.1.8"]
                     [org.clojars.kyleburton/clj-etl-utils "1.3.6"]]
  :profiles {:1.2 {:dependencies [[org.clojure/clojure "1.2.1"]
                                  [org.clojure/clojure-contrib "1.2.0"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]
                                  [org.clojure/data.json "0.2.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]
                                  [org.clojure/data.json "0.2.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0-alpha5"]
                                  [org.clojure/data.json "0.2.0"]]}})
