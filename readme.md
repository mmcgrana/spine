# Spine

A high-level, [Beanstalkd](http://kr.github.com/beanstalkd/)-backed job queueing library for Clojure.


## Usage

Initialize the Spine client:

    (require '[spine.client :as spine])
    
    (def sp (spine/init))
    
Enqueue jobs:

    (spine/enqueue sp "call-later")

    (spine/enqueue sp "call-later-with-arg" "arg")
    
Work jobs:

    (defn call-later []
      (println "called!"))

    (defn call-later-with-arg [arg]
      (println "called with" arg "!"))

    (spine/work sp [#'call-later #'call-later-with-arg])


## Example

In this example, we'll show how to perform some background work as the result
of a web request.

Our `web` namespace in `src/web.clj` looks like:

    (ns web
      (:use compojure.core)
      (:use ring.adapter.jetty)
      (:require [spine.client :as client]))
    
    (def sp (spine/init))
    
    (defroutes app
      (POST "/upcase" [word]
        (println "upcase of" word "requested")
        (spine/enqueue sp "upcase" word)
        "upcasing asynchronously\n"))
    
    (defn -main []
      (run-jetty app {:port 8080}))

Then our `worker` namespace in `src/worker.clj` is:

    (ns worker
      (:require [spine.client :as spine]))

    (def sp (spine/init))

    (defn upcase [word]
      (println "the upcase of" word "is" (.toUpperCase word)))
    
    (defn -main []
      (spine/work sp [#'upcase]))

Here is the `project.clj` that has the dependencies we need to run this:

    (defproject spine-demo "0.0.3"
      :dependencies
        [[org.clojure/clojure "1.3.0-alpha4"]
         [ring/ring-jetty-adapter "0.3.5"]
         [compojure "0.5.3"]
         [spine "0.0.3"]])

After writing these files, run `lein deps` to install your dependencies.
Then start three processes as follows:

    $ beanstalkd
    $ lein run -m web
    $ lein run -m worker

When everything is running, test out your app with:

    $ curl -X POST http://localhost:8080/upcase?word=beanstalkd

The `curl` command should return:

    upcasing asynchronously

You should see in your web logs:

    spine event=init url='beanstalkd://127.0.0.1:11300'
    upcase of word beanstalkd requested
    spine event=enqueue queue='upcase'

And in your worker logs:

    spine event=init url='beanstalkd://127.0.0.1:11300'
    spine event=work queues='upcase'
    spine event=dequeue queue=upcase
    the upcase of beanstalkd is BEANSTALKD
    spine event=complete queue=upcase elapsed=7


## Options

When initializing the client, you can specify the Beanstalkd URL:

    (def sp (spine/init {:url "beanstalkd://queues.myapp.com:9000"}))

It defaults to `"beanstalkd://127.0.0.1:11300"`.

When working jobs, you can specify an error handler that will be invoked with
the `Exception` instance

    (spine/work sp [#'job]
      {:error (fn [e] (send-email (pr-str "it broke:" e)))})


## See also

The companion project [Twine](https://github.com/mmcgrana/twine) provides a protocol-level interface to Beanstalkd that may be more appropriate for some applications.


## Installation

Depend on `[spine "0.0.3"]` in your `project.clj`.
