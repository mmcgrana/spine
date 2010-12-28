# Spine

Redis-backed job queueing for Clojure.


## Usage

Initialize the Spine client:

    (require '[spine.client :as spine])
    
    (def sp (spine/init))
    
Put jobs on the queue:

    (spine/enqueue sp #'call-later)

    (spine/enqueue sp #'call-later-with-arg "arg")
    
Work jobs off the queue:

    (spine/work sp)

Interrogate and manage the queue:

    (spine/depth sp)

    (spine/clear sp)


## Example

In this example, we'll show how to perform some background work as the result
of a web request.

Our `web` namespace in `src/web.clj` looks like:

    (ns web
      (:use compojure.core)
      (:use ring.adapter.jetty)
      (:require worker)
      (:require [spine.client :as client]))
    
    (def sp (spine/init))
    
    (defroutes app
      (POST "/upcase" [word]
        (println "upcase of" word "requested")
        (spine/enqueue sp #'worker/upcase word)
        "upcasing asynchronously\n"))
    
    (defn -main []
      (run-jetty app {:port 8080}))

Then our `worker` namespace in `src/worker.clj` is:

    (ns worker
      (:require [spine.client :as spine]))
     
    (defn upcase [word]
      (println "the upcase of" word "is" (.toUpperCase word)))
    
    (defn -main []
      (spine/work (spine/init)))

Here is the `project.clj` that has the dependencies we need to run this:

    (defproject spine-demo "0.0.1"
      :dependencies
        [[org.clojure/clojure "1.3.0-alpha4"]
         [ring/ring-jetty-adapter "0.3.5"]
         [compojure "0.5.3"]
         [spine "0.0.1"]])

After writing these files, run `lein deps` to install your dependencies.
Then start three processes as follows:

    $ redis-server
    $ lein run -m web
    $ lein run -m worker

When everything is running, test out your app with:

    $ curl -X POST http://localhost:8080/upcase?word=redis

The `curl` command should return:

    upcasing asynchronously

You should see in your web logs:

    spine event=init url='redis://127.0.0.1:6379' queue='spine:queue'
    upcase of word redis requested
    spine event=enqueue fn='worker/upcase'

And in your worker logs:

    spine event=init url='redis://127.0.0.1:6379' queue='spine:queue'
    spine event=work
    spine event=dequeue fn='worker/upcase'
    the upcase of redis is REDIS
    spine event=complete fn='worker/upcase' elapsed=1


## Options

When initializing the client, you can specify the Redis URL and Redis key to
use for the Spine queue:

    (def sp (spine/init {:url "redis://password@redis.myapp.com:9000"
                         :queue "q:background"}))

These default to `"redis://127.0.0.1:6379"` and "spine:queue" respectively.

When working jobs, you can specify an error handler that will be invoked with
the `Exception` instance

    (spine/work sp {:error (fn [e] (send-email (pr-str "it broke:" e)))})


## Installation

Depend on `[spine "0.0.1"]` in your `project.clj`.
