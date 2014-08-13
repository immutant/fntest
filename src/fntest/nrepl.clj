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
            [backtick]
            [clojure.string :as str]
            [fntest.jboss :as jboss]))

(def ^:dynamic *nrepl-conn*)

(defn get-host [& [opts]]
  (or (:host opts) "localhost"))

(defn get-port [& [opts]]
  (cond
   (:port opts) (read-string (:port opts))
   (:port-file opts) (read-string (slurp (:port-file opts)))
   :default 7888))

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
  ;; (println "\n  - Executing: " command)
  (let [result (parse (remote command))]
    ;; (println "    - Result:" result)
    (if (:out result)
      (println (:out result)))
    (if (:value result)
      (try
        (read-string (:value result))
        (catch java.lang.RuntimeException e
          (if-not (= "Unreadable form" (.getMessage e))
            (throw e)))))))

(defn midje-tests
  "Invokes the Midje test suite in the remote Clojure."
  [nses]
  (println "Running Midje tests...")
  (execute (pr-str (backtick/template (midje.util.ecosystem/set-leiningen-paths!
                                       {:test-paths [(immutant.util/app-relative "test")]
                                        :source-paths [(immutant.util/app-relative "src")]}))))
  (let [failures-count (:failures (execute (pr-str (backtick/template (midje.repl/load-facts)))))
        success? (= failures-count 0)]
    (println "Midje tests done." failures-count "tests failed.")
    success?))

(defn expectations-tests
  "Invokes the expectations test suite in the remote Clojure."
  [nses]
  (println "Running expectations tests...")
  (println "Testing namespaces in container: " nses)
  (execute (pr-str (backtick/template (apply require '~nses))))
  (execute (pr-str (backtick/template (expectations/disable-run-on-shutdown))))
  (let [{:keys [error fail]} (execute (pr-str (backtick/template (expectations/run-tests '~nses))))]
    (and (zero? error) (zero? fail))))

(defn clojure-test-tests
  "Invokes the clojure.test test suite in the remote Clojure."
  [nses]
 (println "Running clojure.test tests...")
 (println "Testing namespaces in container:" nses)
 (execute (pr-str (backtick/template (apply require '~nses))))
 (execute (pr-str (backtick/template (clojure.test/successful? (apply clojure.test/run-tests '~nses))))))

(defn try-require
  [ns]
  (execute (pr-str (backtick/template (try
                                        (require '~ns)
                                        true
                                        (catch java.io.FileNotFoundException _
                                          false))))))

(defn select-test-runner
  []
  (cond
   (try-require 'midje.repl)   midje-tests
   (try-require 'expectations) expectations-tests
   (try-require 'clojure.test) clojure-test-tests
   :else (throw (Exception. "Failed to load a test runner"))))

(defn run-tests
  "Load test namespaces beneath dir and run them"
  [{:keys [nses] :as opts}]
  (if (seq nses)
    (do
      (println "Connecting to remote app...")
      (with-connection opts
        (let [test-runner (select-test-runner)]
          (test-runner nses))))
    (do
      (println "No tests found.")
      true)))
