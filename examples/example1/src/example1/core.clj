(ns example1.core
  (:require
   [org.httpkit.server :as server]
   [kinematic.core :as kcore]
   [kinematic.dsl  :as kdsl]))

;; TODO: kinematic was created for 'api first' web apps, to support
;; (for the most part) single page apps.  The example should
;; demonstrate that paradigm.

;; TODO: stop assuming Jetty
;; TODO: Pattern matchers should collapse adjacent slashes
;; TODO: Mount multiple apps at distinct mount points.
;; TODO: Output Serializers:
;;   Dispatch Table based on the requested ContentType, there are several default behaviors:
;;      application/json:  takes whatever data strucutre the handler returns and runs it through clojure.data.json/json-str
;;      application/xml:   takes whatever data strucutre the handler returns and does what?
;;      text/html:         takes whatever data strucutre the handler returns and does what?
;;      tex/plain:         takes whatever data strucutre the handler returns and calls str on it.
;; What's the minimum we can say to get an app up and running?

;; TODO: how to bake in testing for kinematic?

(kdsl/defweb :example1
  :mount-point       "/"

  ;; :app-ns-prefix is spidered by Kinematic, which will require any
  ;; namespaces that use this prefix.  This lets you write new
  ;; handlers without having to explicitly require them anywhere else
  ;; in your code.
  :app-ns-prefix     :example1.web

  ;; :before-middleware is an optional vector of ring middleware.
  :before-middleware []

  ;; NB: is this even supported?
  :after-middleware  []

  ;; NB: should there be default behavior here?
  :404-page          "resources/public/404.html")

(defonce stop-server-fn (atom nil))

(defn service-main []
  (reset! stop-server-fn (server/run-server (kdsl/dyn-handler :example1)
                                            {:port 8888})))

(comment
  (@stop-server-fn)
  (service-main)
  )
