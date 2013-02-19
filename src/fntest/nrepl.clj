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
            [fntest.jboss :as jboss]))

(def ^:dynamic *nrepl-conn*)

(defn run-command [nses]
  (str/replace
   (repl/code
    (try
      (require 'clojure.test)
      (apply require 'REPLACE)
      (clojure.test/successful? (apply clojure.test/run-tests 'REPLACE))
      (catch Exception e
        (.printStackTrace e)
        (.printStackTrace e *out*)
        nil)))
   "REPLACE"
   (pr-str nses)))

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
  (let [result (parse (remote command))]
    (if (:out result)
      (println (:out result)))
    (if (:value result)
      (read-string (:value result)))))

(defn run-tests
  "Load test namespaces beneath dir and run them"
  [{:keys [nses] :as opts}]
  (println "Testing namespaces in container:" nses)
  (with-connection opts
    (execute (run-command nses))))

