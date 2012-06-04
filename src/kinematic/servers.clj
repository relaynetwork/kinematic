(ns kinematic.servers
  (:use
   ring.adapter.jetty))

(def *default-jetty-config*
  {:port 8089 :join? false})

(defonce *jetty* (atom nil))

(defn start-jetty [app]
  (when-not @*jetty*
    (reset! *jetty*
            (run-jetty app *default-jetty-config*))))

(defn stop-jetty []
  (when @*jetty*
    (.stop @*jetty*)
    (reset! *jetty* nil)))

(defn restart-jetty-server [app]
  (stop-jetty)
  (start-jetty app)
  :jetty-started)
