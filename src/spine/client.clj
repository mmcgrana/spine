(ns spine.client
  (:require [clj-redis.client :as redis]))

(defn- fn-serialize [fn-var]
  (format "%s/%s" (.getName (.ns fn-var)) (.sym fn-var)))

(defn- fn-deserialize [fn-str]
  (resolve (symbol fn-str)))

(defn init [& [{:keys [url queue]}]]
  (let [url (or url "redis://127.0.0.1:6379")
        queue (or queue "spine:queue")
        db (redis/init {:url url})]
    (redis/ping db)
    (printf "spine event=init url='%s' queue='%s'\n" url queue) (flush)
    {:db db :queue queue}))

(defn enqueue [{:keys [db queue]} fn-var & [arg]]
  (let [fn-str (fn-serialize fn-var)
        payload (pr-str [fn-str arg])]
    (printf "spine event=enqueue fn='%s'\n" fn-str) (flush)
    (redis/lpush db queue payload)))

(defn depth [{:keys [db queue]}]
  (redis/llen db queue))

(defn clear [{:keys [db queue]}]
  (redis/del db [queue]))

(defn work [{:keys [db queue]} & [{:keys [error]}]]
  (printf "spine event=work\n") (flush)
  (loop []
    (when-let [[_ payload] (redis/brpop db [queue] 10)]
      (try
        (let [start (System/currentTimeMillis)
              [fn-str arg] (read-string payload)
              fn-val (fn-deserialize fn-str)]
          (printf "spine event=dequeue fn='%s'\n" fn-str) (flush)
          (if arg (fn-val arg) (fn-val))
          (let [end (System/currentTimeMillis)
                elapsed (- end start)]
            (printf "spine event=complete fn='%s' elapsed=%d\n" fn-str elapsed) (flush)))
        (catch Exception e
          (println *err* "spine event=exception")
          (.printStackTrace e)
          (when error
            (error e)))))
   (recur)))
