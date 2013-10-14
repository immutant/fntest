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
  (:require [fntest.jboss    :as jboss]
            [fntest.nrepl    :as nrepl]
            [bultitude.core  :as bc]
            [clojure.java.io :as io]))

(def port-file "target/test-repl-port")

(def default-modes #{:isolated :offset})

(def current-endpoint (atom nil))

(defn some-endpoint [modes]
  (or @current-endpoint
      (jboss/default-endpoint modes)))

(defn with-jboss
  "A test fixture for starting/stopping JBoss"
  ([f]
     (with-jboss default-modes f))
  ([modes f]
     (with-jboss modes nil f))
  ([modes endpoint f]
     (let [endpoint (or endpoint (some-endpoint modes))
           running? (jboss/wait-for-ready? endpoint 0)]
       (try
         (when-not running?
           (reset! current-endpoint
                   (deref (jboss/start modes) 60000 :timeout))
           (jboss/wait-for-ready? @current-endpoint 30))
         (f)
         (catch Throwable e
           (.printStackTrace e))
         (finally
           (when-not running?
             (jboss/stop @current-endpoint)))))))

(defn with-deployments
  "Returns a test fixture for deploying/undeploying multiple apps to a running JBoss"
  ([descriptor-map]
     (with-deployments true descriptor-map))
  ([modes descriptor-map]
     (fn [f]
       (if (jboss/wait-for-ready? (some-endpoint modes)
                                  (Integer. (or (System/getenv "WAIT_FOR_JBOSS") 60)))
         (try
           (jboss/deploy (some-endpoint modes) descriptor-map)
           (f)
           (finally
             (apply jboss/undeploy (some-endpoint modes) (keys descriptor-map))))
         (println "Timed out waiting for JBoss (try setting WAIT_FOR_JBOSS=120)")))))

(defn with-deployment
  "Returns a test fixture for deploying/undeploying an app to a running JBoss"
  ([name descriptor-or-file]
     (with-deployment true name descriptor-or-file))
  ([modes name descriptor-or-file]
      (with-deployments modes {name descriptor-or-file})))

(defn locate-tests
  "Locates the namespaces to be tested."
  [root dirs]
  (mapcat #(bc/namespaces-in-dir %) (or dirs [(io/file root "test")])))

(defn test-in-container
  "Starts up an Immutant, if necessary, deploys an application named
   by name and located at root, and invokes f, after which the app is
   undeployed, and the Immutant, if started, is shut down"
  [name root & {:keys [jboss-home config dirs profiles modes api-endpoint]
                :or {jboss-home jboss/*home*
                     profiles [:dev :test]
                     modes default-modes}
                :as opts}]
  (binding [jboss/*home* jboss-home]
    (let [deployer (with-deployment modes name 
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
      (with-jboss modes api-endpoint #(deployer f)))))
