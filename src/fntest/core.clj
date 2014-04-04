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

(ns fntest.core
  (:require [jboss-as.management :as api]
            [fntest.jboss        :as jboss]
            [fntest.nrepl        :as nrepl]
            [bultitude.core      :as bc]
            [clojure.java.io     :as io]))

(def port-file "target/test-repl-port")

(def default-modes #{:isolated :offset})

(def ^:dynamic *server*)

(defn with-jboss
  "A test fixture for starting/stopping JBoss"
  ([f]
     (with-jboss default-modes f))
  ([modes f]
     (binding [*server* (jboss/create-server modes)]
       (let [running? (api/wait-for-ready? *server* 0)]
         (try
           (when-not running?
             (println "Starting JBoss")
             (api/start *server*)
             (api/wait-for-ready? *server* 30))
           (f)
           (catch Throwable e
             (.printStackTrace e))
           (finally
             (when-not running?
               (println "Stopping JBoss")
               (api/stop *server*))))))))

(defn offset-port
  "Resolves port based on the server's offset, if any. Keywords may be
   passed, e.g. :http, :messaging, :remoting, :management-native. The
   optional host arg refers to the name of a host in a domain"
  [port & [host]]
  (api/port *server* port host))

(defn with-deployments
  "Returns a test fixture for deploying/undeploying multiple apps to a running JBoss"
  [descriptor-map]
  (fn [f]
    (if (api/wait-for-ready? *server* (Integer. (or (System/getenv "WAIT_FOR_JBOSS") 60)))
      (try
        (jboss/deploy *server* descriptor-map)
        (f)
        (finally
          (apply jboss/undeploy *server* (keys descriptor-map))))
      (throw (Exception. "Timed out waiting for JBoss (try setting WAIT_FOR_JBOSS=120)")))))

(defn with-deployment
  "Returns a test fixture for deploying/undeploying an app to a running JBoss"
  [name descriptor-or-file]
  (with-deployments {name descriptor-or-file}))

(defn locate-tests
  "Locates the namespaces to be tested."
  [root dirs]
  (mapcat #(bc/namespaces-in-dir %) (or dirs [(io/file root "test")])))

(defn test-in-container
  "Starts up an Immutant, if necessary, deploys an application named
   by name and located at root, and invokes f, after which the app is
   undeployed, and the Immutant, if started, is shut down"
  [name root & {:keys [jboss-home config dirs profiles modes offset log-level]
                :or {jboss-home jboss/*home*
                     profiles [:dev :test]
                     modes default-modes}
                :as opts}]
  (binding [jboss/*home* jboss-home
            jboss/*port-offset* (or offset jboss/*port-offset*)
            jboss/*log-level* (or log-level jboss/*log-level*)]
    (let [deployer (with-deployment name 
                     (merge
                      {:root root
                       :context-path name
                       :lein-profiles profiles
                       :swank-port nil
                       :nrepl-port 0
                       :nrepl-port-file port-file}
                      config))
          f #(nrepl/run-tests (assoc opts
                                :nses (locate-tests root dirs)
                                :port-file (io/file root port-file)))]
      (with-jboss modes #(deployer f)))))
