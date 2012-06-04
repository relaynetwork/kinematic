(ns kinematic.session
  (:use
   kinematic.bindings
   [clj-etl-utils.lang-utils :only [raise]]))

(defn put-session! [& kvs]
  (doseq [[k v] (vec (partition 2 kvs))]
    (swap! *session* assoc k v))
  @*session*)

(defn get-session
  ([k]
     (get @*session* k))
  ([k default]
     (get @*session* k default)))

(defn get-in-session
  ([ks]
     (get-in @*session* ks))
  ([ks default]
     (get-in @*session* ks default)))

(defn dissoc-session! [& ks]
  (reset! *session* (apply dissoc @*session* ks)))


(defmacro session-accessor
  ([pname k]
     (let [fn-name (symbol (format "session-%s" (name pname)))]
       `(defn ~fn-name
          ([]
             (get-session ~k))
          ([v#]
             (put-session! ~k v#)))))
  ([pname]
     (let [fn-name (symbol (format "session-%s" (name pname)))]
       `(defn ~fn-name
          ([]
             (get-session ~pname))
          ([v#]
             (put-session! ~pname v#))))))

(defn reset-session! []
  (reset! *session* {}))