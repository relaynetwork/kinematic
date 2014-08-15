(defproject com.relaynetwork/kinematic "1.3.9"
  :description          "Dynamic Web Development with Clojure"
  :url                  "http://github.com/relaynetwork/kinematic"
  :lein-release         {:deploy-via :clojars :scm :git}
  :repositories         {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :java-source-path     "java"
  :local-repo-classpath true

  :plugins [[lein-release/lein-release "1.0.5"]
            [lein-swank "1.4.5"]]

  :profiles             {:dev {:dependencies []}
                         :1.2 {:dependencies [[org.clojure/clojure "1.2.0"]
                                              [org.clojure/data.json      "0.2.2"]]}
                         :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]
                                              [org.clojure/data.json      "0.2.3"]]}
                         :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]
                                              [org.clojure/data.json      "0.2.3"]]}
                         :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]
                                              [org.clojure/data.json      "0.2.3"]]}
                         :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]
                                              [org.clojure/data.json      "0.2.3"]]}}
  :aliases              {"all" ["with-profile" "dev,1.2:dev,1.3:dev,1.4:dev,1.5:dev,1.6"]}
  :global-vars          {*warn-on-reflection* true}
  :dependencies         [[org.clojure/tools.namespace          "0.1.0"]
                         [ring/ring-core                       "1.1.8"]
                         [ring/ring-jetty-adapter              "1.1.8"]
                         [com.github.kyleburton/clj-etl-utils  "1.0.79"]])
