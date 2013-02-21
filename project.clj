(defproject com.relaynetwork/kinematic "1.2.4"
  :description          "Dynamic Web Development with Clojure"
  :dev-dependencies     [[swank-clojure                        "1.4.2"]]
  :local-repo-classpath true
  :lein-release     {:deploy-via :clojars :scm :git}
  :dependencies     [[org.clojure/clojure                  "1.2.1"]
                     [org.clojure/clojure-contrib          "1.2.0"]
                     [org.clojure/tools.namespace          "0.1.0"]
                     [ring/ring-core                       "1.1.0"]
                     [ring/ring-jetty-adapter              "1.1.0"]
                     [org.clojars.kyleburton/clj-etl-utils "1.0.53"]])
