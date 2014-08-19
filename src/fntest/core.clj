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
  (:require [jboss-as.management       :as api]
            [fntest.jboss              :as jboss]
            [fntest.nrepl              :as nrepl]
            [bultitude.core            :as bc]
            [clojure.java.io           :as io]
            [clojure.string            :as str]
            [leiningen.core.project    :as project]
            [leiningen.core.classpath  :as cp]
            [immutant.deploy-tools.war :as war])
  (:import java.io.File))

(def default-port-file "target/test-repl-port")

(def default-modes #{:isolated :offset})

(def ^:dynamic *server*)

(defn disable-management-security [config]
  (str/replace config
    #"(<http-interface )(security-realm=['\"]ManagementRealm['\"])"
    "$1"))

(defn enable-domain-port-offset [config]
  (when-not (re-find #"port-offset:0" config)
    (str/replace config #"(<server name=\"server-one\" group=\"main-server-group\">)"
      "$1\n<socket-bindings port-offset=\"\\${jboss.socket.binding.port-offset:0}\"/>")))

(defn with-jboss
  "A test fixture for starting/stopping JBoss"
  ([f]
     (with-jboss default-modes f))
  ([modes f]
     (binding [*server* (jboss/create-server modes)]
       (when (jboss/isolated? modes)
         (if (jboss/domain? modes)
           (do
             (api/alter-config! *server* disable-management-security "host.xml")
             (api/alter-config! *server* enable-domain-port-offset "host.xml"))
           (api/alter-config! *server* disable-management-security)))
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
  [deployments-map]
  (fn [f]
    (if (api/wait-for-ready? *server* (Integer. (or (System/getenv "WAIT_FOR_JBOSS") 60)))
      (try
        (jboss/deploy *server* deployments-map)
        (f)
        (finally
          (apply jboss/undeploy *server* (keys deployments-map))))
      (throw (Exception. "Timed out waiting for JBoss (try setting WAIT_FOR_JBOSS=120)")))))

(defn with-deployment
  "Returns a test fixture for deploying/undeploying an app to a running JBoss"
  [name war-file]
  (with-deployments {name war-file}))

(defn locate-tests
  "Locates the namespaces to be tested."
  [root dirs]
  (mapcat #(bc/namespaces-in-dir %) (or dirs [(io/file root "test")])))

(defn enable-dev [project]
  (assoc project :dev? true))

(defn enable-nrepl [project port-file]
  (update-in project [:nrepl] merge
    {:start? true
     :port 0
     :port-file (.getAbsolutePath port-file)}))

(defn set-classpath [project]
  (assoc project :classpath (cp/get-classpath project)))

(defn set-init [project]
  (assoc project :init-fn (symbol (str (:main project)) "-main")))

(defn test-in-container
  "Starts up a container, if necessary, deploys an application named
   by name and located at root, and invokes f, after which the app is
   undeployed, and the Immutant, if started, is shut down"
  [name root & {:keys [jboss-home profiles dirs modes offset log-level war-file port-file]
           :or {jboss-home jboss/*home*
                profiles [:dev :test]
                modes default-modes}
                :as opts}]
  (binding [jboss/*home* jboss-home
            jboss/*port-offset* (or offset jboss/*port-offset*)
            jboss/*log-level* (or log-level jboss/*log-level*)]
    (let [port-file (or port-file (io/file root default-port-file))
          project (project/read
                    (.getAbsolutePath (io/file root "project.clj"))
                    profiles)
          deployer (with-deployment name
                     (or war-file
                       (war/create-war
                         (File/createTempFile "fntest" ".war")
                         (-> project
                           set-classpath
                           set-init
                           enable-dev
                           (enable-nrepl port-file)))))
          f #(if (.exists port-file)
               (nrepl/run-tests (assoc opts
                                   :nses (locate-tests root dirs)
                                   :port-file port-file))
               (println "Oops! Something went wrong. Please check the WildFly server log."))]
      (with-jboss modes #(deployer f)))))
