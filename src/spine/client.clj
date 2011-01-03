(ns spine.client
  (:require [clojure.string :as str])
  (:require [clj-redis.client :as redis])
  (:require [clj-stacktrace.repl :as st]))

(defn log [line & args]
  (apply printf line args)
  (println))

(defn init [& [{:keys [url]}]]
  (let [url (or url "redis://127.0.0.1:6379")
        prefix "spine:queue"
        db (redis/init {:url url})]
    (redis/ping db)
    (log "spine event=init url='%s'" url)
    {:db db :prefix prefix}))

(defn enqueue [{:keys [db prefix]} queue & [arg]]
  (let [payload (pr-str arg)]
    (log "spine event=enqueue queue=%s" queue)
    (redis/lpush db (str prefix ":" queue) payload)))

(defn depth [{:keys [db prefix]} queue]
  (redis/llen db (str prefix ":" queue)))

(defn clear [{:keys [db prefix]} queue]
  (redis/del db [(str prefix ":" queue)]))

(defn work [{:keys [db prefix]} queue-vars & [{:keys [error]}]]
  (let [queue-map (reduce (fn [m v] (assoc m (name (.sym v)) v)) {} queue-vars)
        queue-names (str/join "," (keys queue-map))
        queue-keys (map #(str prefix ":" %) (keys queue-map))]
    (log "spine event=work queues='%s'" queue-names)
    (loop []
      (when-let [[queue-key payload] (redis/brpop db queue-keys 10)]
        (let [queue (last (str/split queue-key #":"))]
          (try
            (let [start (System/currentTimeMillis)
                  arg (read-string payload)
                  fn-val (queue-map queue)]
              (log "spine event=dequeue queue=%s" queue)
              (if arg (fn-val arg) (fn-val))
              (let [end (System/currentTimeMillis)
                    elapsed (- end start)]
                (log "spine event=complete queue=%s elapsed=%d" queue elapsed)))
            (catch Exception e
              (.append *err* (format "spine event=exception queue=%s\n" queue))
              (st/pst-on *err* false e)
              (when error
                (error e))))))
      (recur))))
