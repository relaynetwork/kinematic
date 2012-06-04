(ns kinematic.dsl
  (:require
   [clojure.data.json   :as json]
   [clj-etl-utils.log   :as log])
  (:use
   [clj-etl-utils.lang-utils :only [raise]]
   kinematic.core
   [kinematic.servers      :only [restart-jetty-server]]
   ring.util.response
   ring.middleware.params
   ring.middleware.keyword-params))


(defonce *dyn-handlers* (atom {}))

(defn register-dispatcher [app-name config]
  (swap! *dyn-handlers* assoc app-name config))

(defn get-dispatch-config [app-name]
  (if-not (contains? @*dyn-handlers* app-name)
    (raise "Error: no dispatcher registered for: %s" app-name)
    (get @*dyn-handlers* app-name)))

(defn defapi [app-name patterns & opts]
  (let [opts (apply hash-map opts)]
    (register app-name (merge {:patterns patterns} opts))))

(defn app-route-info [app-name]
  (reduce (fn [accum [route-name route-config]]
            (assoc accum
              route-name {:patterns          (:patterns route-config)
                          :supported-methods (supported-methods (:ns route-config))
                          }))
          {}
          (route-info app-name)))

(defmacro auto-routes [app-name]
  `(do
     (defapi ~app-name ["/routes"])
     (api-get
       {:routes (app-route-info ~app-name)})))

(defmacro dyn-handler [app-name]
  (let [config            (get-dispatch-config app-name)
        not-found-page    (get config :404-page "resources/public/404.html")
        before-middleware (reverse (:before-middleware config))
        after-middleware  (reverse (:after-middleware config))]
    `(-> (fn [req#]
           (let [resp# (ring.util.response/file-response ~not-found-page)]
             (assoc resp#
               :status 404)))
         ~@after-middleware
         (make-dyn-dispatcher
          ~app-name
          (get ~config :db-name :none)
          (get ~config :mount-point "/"))
         ~@before-middleware)))

(defmacro start-dispatcher [app-name]
  `(restart-jetty-server (dyn-handler ~app-name)))

(defmacro defweb [app-name & opts]
  (let [opts          (apply hash-map opts)
        dbname        (get opts :db-name :none)
        mount-point   (get opts :mount-point "/")
        app-ns-prefix (get opts :app-ns-prefix)]
    `(do
       (load-and-register ~app-ns-prefix)
       (auto-routes ~app-name)
       (register-dispatcher ~app-name ~opts)
       (when (:log4j-config-file ~opts)
         (load-log4j-file (:log4j-config-file ~opts))))))

(defn wrap-stacktrace [handler]
  (fn [req]
    (try
     (handler req)
     (catch Exception ex
       (log/infof ex "Got exception: %s" ex)
       {:status 500}))))


(defmacro api-get [& body]
  `(defn ~'get-req [~'request]
     (let [result# (do
                     ~@body)]
       (respond-json result#))))

(defmacro api-delete [& body]
  `(defn ~'delete-req [~'request]
     (let [result# (do
                     ~@body)]
       (respond-json result#))))

(defmacro api-post [& body]
  `(defn ~'post-req [~'request]
     (let [~'body  (json/read-json (slurp (:body ~'request)))
           result# (do
                     ~@body)]
       (respond-json result#))))

(defmacro api-put [& body]
  `(defn ~'put-req [~'request]
     (let [~'body  (json/read-json (slurp (:body ~'request)))
           result# (do
                     ~@body)]
       (respond-json result#))))
