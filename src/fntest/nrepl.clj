;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
;;
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;;
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;;
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns fntest.nrepl
  (:require [clojure.tools.nrepl :as repl]
            [clojure.string :as str]
            [fntest.core :as core]
            [fntest.jboss :as jboss]))

(def ^:dynamic *nrepl-conn*)

(def load-command (repl/code
                   (require '[clojure.test :as t]
                            '[bultitude.core :as b]
                            '[immutant.util :as u])))

(defn deps-command [test-dir]
  (str/replace
   (repl/code
    (require 'immutant.dev)
    (-> (immutant.dev/add-dependencies! "REPLACE" '[bultitude "0.2.0"])
        (select-keys [:dependencies :source-paths])))
   "REPLACE"
   test-dir))

(defn run-command [test-dir]
  (str/replace
   (repl/code
    (let [nses (b/namespaces-in-dir (u/app-relative "REPLACE"))]
      (apply require nses)
      (t/successful? (apply t/run-tests nses))))
   "REPLACE"
   test-dir))

(defn get-host [& [opts]]
  (or (:host opts) "localhost"))

(defn get-port [& [opts]]
  (read-string (or (:port opts) "7888")))

(defmacro with-connection
  "Takes :host and :port options"
  [opts & body]
  `(with-open [c# (repl/connect :host (get-host ~opts) :port (get-port ~opts))]
     (binding [*nrepl-conn* c#]
       ~@body)))

(defn remote
  "Invoke command in remote nrepl"
  [command]
  (-> (repl/client *nrepl-conn* Long/MAX_VALUE)
      (repl/client-session)
      (repl/message {:op :eval :code command})
      doall))

(defn parse
  "Summary of the nrepl results, with :err merged into :out in the correct order"
  [results]
  (reduce
   (fn [m [k v]]
     (case k
       (:out :err) (update-in m [:out] #(str % v))
       (assoc m k v)))
   {} (apply concat results)))

(defn execute [command]
  (println "nrepl>" command)
  (let [result (parse (remote command))]
    (if (:out result) (println (:out result)))
    (println (:value result))
    (read-string (:value result))))

(defn run-tests
  "Load test namespaces beneath dir and run them"
  [opts]
  (let [dir (or (:dir opts) "test")]
    (with-connection opts
      (execute (deps-command dir))
      (execute load-command)
      (execute (run-command dir)))))

(defn run-in-container
  "Starts up an Immutant, if necessary, deploys an application named
   by name and located at root, and invokes f, after which the app is
   undeployed, and the Immutant, if started, is shut down"
  [name root opts]
  (binding [jboss/*home* (:jboss-home opts jboss/*home*)]
    (let [deployer (core/with-deployment name
                     {:root root
                      :context-path (str name "-" (java.util.UUID/randomUUID))
                      :lein-profiles [:default :test]
                      :swank-port nil
                      :nrepl-port (get-port opts)})
          f #(run-tests opts)]
      (core/with-jboss #(deployer f) :lazy))))
