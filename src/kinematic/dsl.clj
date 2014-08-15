(ns kinematic.dsl
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging    :as log])
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

(defmacro defapi [app-name patterns & opts]
  `(let [opts# (hash-map ~@opts)
         url-patterns# (if (vector? ~patterns)
                         ~patterns
                         (vec (distinct (mapcat :patterns (vals ~patterns)))))]
     (register
      ~app-name
      (merge {:patterns url-patterns#
              :methods  (when (map? ~patterns)
                          ~patterns)
              :options  '~opts}
             opts#))))

(defn app-route-info [app-name]
  (reduce (fn [accum [route-name route-config]]
            (assoc accum
              route-name {:patterns          (:patterns route-config)
                          :supported-methods (supported-methods route-config)}))
          {}
          (route-info app-name)))

(defmacro auto-routes [app-name]
  `(do
     (defapi ~app-name ["/routes"])
     ;; NB: need to come up with a better solution for before/after middleware.
     ;; They are causing JSON parse errors. Just dissocing for now
     (api-get
       {:routes   (app-route-info ~app-name)
        :app-info (dissoc (get-dispatch-config ~app-name) :before-middleware :after-middleware)})))

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

(defn app-handler [& app-names]
  (let [apps (map
              (fn [app-name]
                (let [app-cfg           (get-dispatch-config app-name)
                      not-found-page    (get app-cfg :404-page "resources/public/404.html")
                      before-middleware (reverse (:before-middleware app-cfg))
                      after-middleware  (reverse (:after-middleware app-cfg))
                      mount-point       (:mount-point app-cfg "/")
                      handler-fn        (fn [req]
                                          (assoc (ring.util.response/file-response not-found-page)
                                            :status 404))
                      handler-fn        (reduce
                                         (fn [acc mw]
                                           (mw acc))
                                         handler-fn
                                         after-middleware)
                      handler-fn         (make-dyn-dispatcher
                                          handler-fn
                                          app-name
                                          (get app-cfg :db-name :none)
                                          mount-point)
                      handler-fn        (reduce
                                         (fn [acc mw]
                                           (mw acc))
                                         handler-fn
                                         before-middleware)]
                  [mount-point
                   handler-fn
                   {:app-cfg           app-cfg
                    :not-found-page    not-found-page
                    :before-middleware before-middleware
                    :after-middleware  after-middleware
                    :handler-fn        handler-fn
                    :mount-point       mount-point}]))
              app-names)]
    (fn [req]
      (let [uri (:uri req)]
       (loop [[app & apps] apps]
         (let [[mount-point handler-fn app-cfg] app]
           (cond
             (not app)
             nil     ;; not handled by us

             (.startsWith uri mount-point)
             (handler-fn req)

             :no-match
             (recur apps))))))))

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
       (register-dispatcher ~app-name ~opts))))

(defn wrap-stacktrace [handler]
  (fn [req]
    (try
     (handler req)
     (catch Exception ex
       (log/infof ex "Got exception: %s" ex)
       {:status 500}))))


(defn read-body-as-json [request]
  (try
   [true
    (json/read-str
     (->
      request
      :body
      slurp)
     :key-fn keyword)]
   (catch Exception ex
     (log/infof ex "Error reading request body as json: %s :: " ex)
     [false nil])))

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
     (let [[status# ~'body]  (read-body-as-json ~'request)]
       (if status#
         (respond-json (do ~@body))
         {:status 400
          :body (json/write-str {:status "BadRequest"})}))))

(defmacro api-put [& body]
  `(defn ~'put-req [~'request]
     (let [[status# ~'body]  (read-body-as-json ~'request)]
       (if status#
         (respond-json (do ~@body))
         {:status 400
          :body (json/write-str {:status "BadRequest"})}))))
