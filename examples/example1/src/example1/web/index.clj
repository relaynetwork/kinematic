(ns example1.web.index
  (:require
   [kinematic.dsl :as kdsl]))

(kdsl/defapi :example1 ["index"])

(kdsl/api-get
  {:status "OK" :message "Hello"})