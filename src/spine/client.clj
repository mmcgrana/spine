(ns spine.client
  (:require [clojure.string :as str])
  (:require [twine.client :as twine])
  (:require [clj-stacktrace.repl :as st])
  (:import clojure.lang.Var))

(defn log [line & args]
  (apply printf line args)
  (println))

(defn init [& [{:keys [url]}]]
  (let [url (or url "beanstalkd://127.0.0.1:11300")
        tw (twine/init {:url url})]
    (log "spine event=init url='%s'" url)
    tw))

(defn enqueue [tw queue & args]
  (let [data (pr-str [queue args])]
    (log "spine event=enqueue queue=%s" queue)
    (twine/use tw queue)
    (twine/put tw data)))

(defn work [tw queue-vars & [{:keys [error]}]]
  (let [queue-map (reduce (fn [m ^Var v] (assoc m (name (.sym v)) v)) {} queue-vars)
        queues (keys queue-map)]
    (log "spine event=work queues='%s'" (str/join "," queues))
    (doseq [queue queues] (twine/watch tw queue))
    (loop []
      (when-let [{:keys [id data]} (twine/reserve tw)]
        (let [[queue args] (read-string data)]
          (try
            (let [start (System/currentTimeMillis)
                  fn-val (queue-map queue)]
              (log "spine event=dequeue queue=%s" queue)
              (apply fn-val args)
              (twine/delete tw id)
              (let [end (System/currentTimeMillis)
                    elapsed (- end start)]
                (log "spine event=complete queue=%s elapsed=%d" queue elapsed)))
            (catch Exception e
              (.append *err* (format "spine event=exception queue=%s\n" queue))
              (st/pst-on *err* false e)
              (when error
                (error e))))))
      (recur))))
