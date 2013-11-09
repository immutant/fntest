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

(ns fntest.jboss
  (:require [clojure.java.io     :as io]
            [jboss-as.management :as api]))

(def ^:dynamic *home*
  (or (System/getenv "JBOSS_HOME")
      (io/file (System/getProperty "user.home") ".immutant/current/jboss")))
(def ^:dynamic *descriptor-root* ".descriptors")
(def ^:dynamic *isolation-dir* "target/isolated-immutant")
(def ^:dynamic *port-offset* 67)

(defn- check-mode [mode modes]
  (boolean
   (or (= mode modes)
       (and (set? modes)
            (some #{mode} modes)))))

(def isolated? (partial check-mode :isolated))

(def offset? (partial check-mode :offset))

(def domain? (partial check-mode :domain))

(def debug? (partial check-mode :debug))

(defn isolated-base-dir [modes jboss-home]
  (let [base (if (domain? modes) "domain" "standalone")]
    (if (isolated? modes)
      (let [base-dir (io/file *isolation-dir* base)
            config-dir (io/file jboss-home base "configuration") ]
        (mapv (fn [f] (.mkdirs (io/file base-dir f)))
              ["deployments" "configuration"])
        (mapv (fn [f] (io/copy (io/file config-dir f)
                              (io/file base-dir "configuration" f)))
              (.list config-dir
                     (reify java.io.FilenameFilter
                       (accept [_ dir name]
                         (boolean (or (re-find #"\.properties$" name)
                                      (re-find #"\.xml$" name)))))))
        (.getCanonicalPath base-dir))
      (.getCanonicalPath (io/file jboss-home base)))))

(defn create-server [modes]
  (api/create-server :domain (domain? modes)
                     :offset (if (offset? modes) *port-offset* 0)
                     :jboss-home *home*
                     :base-dir (isolated-base-dir modes *home*)
                     :debug (debug? modes)))

(defn wait-for-ready?
  [server attempts]
  (api/wait-for-ready? server attempts))

(defn ready? [server]
  (api/ready? server))

(defn start
  "Start up a JBoss, returning a promise that gives you its management url"
  [server]
  (println "Starting JBoss")
  (api/start server))

(defn stop
  "Shut down whatever JBoss instance is responding to api-url"
  [server]
  (println "Stopping JBoss")
  (api/stop server))

(defn descriptor
  "Return a File object representing the deployment descriptor"
  [name & [content]]
  (let [fname (if (re-seq #".+\.clj$" name) name (str name ".clj"))
        file (io/file *descriptor-root* fname)]
    (when content
      (io/make-parents file)
      (spit file (into content {:root (str (.getCanonicalFile (io/file (:root content))))})))
    file))

(defn deployment-name
  "Determine the name for the deployment."
  [name]
  (if (.endsWith name ".ima")
    name
    (.getName (descriptor name))))

(defn deploy
  "Create an app deployment descriptor from the content and deploy it"
  ([server content-map]
     (doseq [[name content] content-map]
       (deploy server name content)))
  ([server name content]
     (let [file (if (instance? java.io.File content) content (descriptor name content))
           fname (.getName file)
           url (.toURL file)]
       (println "Deploying" (.getCanonicalPath file))
       (api/deploy server fname url))))

(defn undeploy
  "Undeploy the apps deployed under the given names"
  [server & names]
  (doseq [name names]
    (println "Undeploying" name)
    (api/undeploy server (deployment-name name))))
