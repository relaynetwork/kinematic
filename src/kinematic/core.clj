(ns kinematic.core
  (:require
   [clojure.data.json     :as json]
   [clj-etl-utils.log     :as log])
  (:use
   [clj-etl-utils.lang-utils :only [raise]]
   ring.adapter.jetty
   kinematic.bindings
   clojure.tools.namespace))

(defonce *routes* (atom {}))

;; NB: can we do this automatically w/meta-data on the namespace?
(defn register* [handler-ns app-name route-mapping]
  (swap! *routes* assoc-in [app-name handler-ns] route-mapping))

(defmacro register [app-name route-mapping]
  `(register* ~'*ns* ~app-name (assoc ~route-mapping :ns ~'*ns* :app-name ~app-name)))

(defn unregister* [app-name handler-ns]
  (swap! *routes*
         assoc app-name
         (dissoc (get @*routes* app-name)
                 handler-ns)))

(defn route-info [app-name]
  (if-not (contains? @*routes* app-name)
    (raise "Error: %s is not a registered app: %s" app-name (keys @*routes*))
   (get @*routes* app-name {})))

(defn before-filters [request route-info]
  (concat
   (get route-info :before-all [])
   (get route-info (keyword (str "before-" (name (:request-method request)))) [])))

(defn run-before-filters [request route-info]
  (loop [[flt & flts] (before-filters request route-info)]
    (if (not flt)
      nil
      (let [res (flt request)]
        (log/infof "before filter: %s => %s" flt res)
        (if (map? res)
          res
          (recur flts))))))

(defn is-authorized? [request route-info]
  (log/infof "checking authorization for request %s against route %s" request route-info)
  (let [users-roles (get @*session* :roles)
        users-roles (if users-roles
                      (json/read-str users-roles)
                      {})
        required-roles (:roles route-info)
        auth-fn        (:auth-fn route-info)]
    (if auth-fn
      (auth-fn request)
      (some (fn [r]
              (get users-roles r))
            required-roles))))

;; NB: we should pre-compile
(defn route-pattern-matches? [uri route-mapping pattern]
  (loop [[ppart & pparts] (.split pattern "/")
         [upart & uparts] (.split uri "/")
         route-params     {}]
    (cond
      (and (nil? ppart)
           (nil? upart))
      [true route-mapping route-params]

      (or (nil? ppart) (nil? upart))
      [false route-mapping route-params]

      (and (.startsWith ppart ":")
           (not (nil? upart)))
      (recur pparts uparts
             (assoc route-params
               (keyword (.substring ppart 1))
               upart))

      (= upart ppart)
      (recur pparts uparts route-params)

      :else
      [false route-mapping route-params])))

(defn route-matches? [uri [route-ns route-mapping]]
  (loop [[pattern & patterns] (if (:patterns route-mapping)
                                (:patterns route-mapping)
                                [(:pattern route-mapping)])
         match-result         (route-pattern-matches? uri route-mapping pattern)]
    (cond
      ;; nothing more to check, this is our result
      (empty? patterns)
      match-result

      ;; this route hit
      (first match-result)
      match-result

      ;; run the next one
      :else
      (recur patterns (route-pattern-matches? uri route-mapping (first patterns))))))

(defn respond-json [resp & [protocol-status]]
  {:status  (or protocol-status 200)
   :headers {"Content-Type" "application/json"}
   :body    (json/write-str resp)})

(defn find-method-in-ns [method handler-ns]
  (let [method-name (symbol (str (name method) "-req"))]
    (ns-resolve handler-ns method-name)))

(defn supported-methods [route-config]
  (reduce
   (fn [accum meth]
     (assoc accum
       meth (if-not (empty? (:methods route-config))
              (get-in route-config [:methods meth])
              {:patterns (:patterns route-config)})))
   {}
   (filter
    (fn [method]
      (find-method-in-ns method (:ns route-config)))
    [:get :post :put :delete :head :options :trace :connect])))



(defn resolve-handler [db-name mount-point app-name request]
  (let [uri             (.substring (:uri request) (count mount-point))
        app-routes      (get @*routes* app-name)
        ;; TODO: have this stop at the first matching route (speed)
        match-results   (map (partial route-matches? uri) app-routes)
        matching-routes (filter #(first %) match-results)]
    (if (= 1 (count matching-routes))
      (binding [*session* (atom (get-in request [:session] {}))]
        (let [[_ route-info route-params]  (first matching-routes)
              handler-ns  (:ns route-info)
              method      (:request-method request)
              method-name (symbol (str (name method) "-req"))]
          (if-let [handler-fn (find-method-in-ns method handler-ns)]
            ;; run the filters, if any return a ring response
            (let [auth-result  (is-authorized?  (assoc request :route-params route-params) route-info)]
              (cond (false? auth-result)
                    (do
                      (log/infof "DynDispatcher: NOT authorized: %s" request)
                      (if (= (get-in request [:headers "accept"]) "application/json")
                        (respond-json {:status 401 })
                        {:status  401
                         :headers {"Content-type" "text/plain"}
                         :body    "Error: not authorized"}))

                    (map? auth-result)
                    auth-result

                    :else ;;true
                    (try
                     (let [filter-result (run-before-filters (assoc request :route-params route-params) route-info)]
                       (if (map? filter-result)
                         (assoc filter-result  :session @*session*)
                         (let [db-name  (:db-conn route-info db-name)
                               result   (if (= db-name :none)
                                          (handler-fn (assoc request :route-params route-params))
                                          (handler-fn (assoc request :route-params route-params)))
                               return   (assoc result :session @*session*)]
                           (if (and (:body return)
                                    (not (= String (class (:body return))))
                                    (not (= java.io.File (class (:body return))))
                                    (not (isa? (class (:body return)) java.io.InputStream)))
                             (raise "Error in handler %s, did not result in a body of type String/File/InputStream: %s" method-name (class (:body return)))
                             return))))
                     (catch Exception ex
                       (log/fatalf ex "API Request Failed Handler [%s] exception! : %s" route-info ex)
                       {:status  500
                        :headers {}
                        :body    "Internal Server Error.  API Call Failed."}))))
            nil)))
      (do
        (log/warnf "0 or > 1 matches detected for uri(%s), matching routes: (%s)" uri (vec matching-routes))
        nil))))

(defn make-dyn-dispatcher [handler app-name db-name mount-point]
  (fn dyn-dispatcher [request]
    (if (not (.startsWith (:uri request) mount-point))
      (do
        (log/infof "uri:%s did not start with mount-point:%s, deferring to %s" (:uri request) mount-point handler)
        (try
         (let [res (handler request)]
           res)
         (catch Exception ex
           (log/errorf ex "Deferred handler threw: %s" ex)
           (throw ex))))
      (if-let [resp (try
                     (resolve-handler db-name mount-point app-name request)
                     (catch Exception ex
                       (log/infof ex "Error in resolve handler: db-name:%s mount-point:%s app-name:%s request:%s %s"
                                  db-name mount-point app-name request ex)
                       {:status 500 :body "Internal Server Error"}))]
        resp
        (do
          (log/infof "Since no routes matched, deferring to %s(%s)" handler request)
          (handler request))))))


(defn load-and-register [pfx]
  (doseq [target-ns
          (filter (fn [s]
                    (.startsWith (str s)
                                 (name pfx)))
                  (find-namespaces-on-classpath))]
    (try
     (require target-ns)
     (catch Exception ex
       (log/fatalf ex "Error requiring: %s" target-ns)
       (throw (RuntimeException. (format "Error requiring: %s => %s" target-ns ex) ex))))))


(defn load-log4j-file [log4j-prop-file]
  (log/infof "Configuring log4j from %s\n" log4j-prop-file)
  (if (.exists (java.io.File. log4j-prop-file))
    (org.apache.log4j.PropertyConfigurator/configure
     (doto (java.util.Properties.)
       (.load (java.io.FileReader. log4j-prop-file))))
    (with-open [res (.getResourceAsStream (class "") log4j-prop-file)]
      (when (nil? res)
        (raise "Error: log4j configuration not found as resource: %s" log4j-prop-file))
      (let [p (doto (java.util.Properties.)
                (.load res))]
        (org.apache.log4j.PropertyConfigurator/configure p)))))
