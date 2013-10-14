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
  (:require [fntest.sh           :as sh]
            [clojure.java.io     :as io]
            [clojure.string      :as str]
            [jboss-as.management :as api]))

(def ^:dynamic *home* (or (System/getenv "JBOSS_HOME")
                          (io/file (System/getProperty "user.home") ".immutant/current/jboss")))
(def ^:dynamic *descriptor-root* ".descriptors")
(def ^:dynamic *isolation-dir* ".isolated-test-data")

(defn- check-mode [mode modes]
  (or (= mode modes)
      (and (set? modes)
           (some #{mode} modes))))

(def isolated? (partial check-mode :isolated))

(def lazy? (partial check-mode :lazy))

(def offset? (partial check-mode :offset))

(defn sysprop [name default]
  (format "-D%s=%s"
          name
          (if-let [val (System/getProperty name)]
            val
            default)))

(defn default-endpoint [modes]
  (if (offset? modes)
    (let [offset (Integer. (or (System/getProperty "jboss.socket.binding.port-offset") 100))
          port (nth (str/split api/*api-endpoint* #"[:/]") 4)]
      (str/replace api/*api-endpoint* port (str (+ offset (Integer. port)))))
    api/*api-endpoint*))

(defn debug-options []
  (if (= "true" (System/getProperty "fntest.debug"))
    "-Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=y"))

(defn make-disabled-scanner-conf []
  (let [conf-dir (io/file *home* "standalone/configuration")]
    (spit (io/file conf-dir "standalone-disabled-scanner.xml")
          (str/replace
           (slurp (io/file conf-dir "standalone.xml"))
           #"(?s)<subsystem xmlns=\"urn:jboss:domain:deployment-scanner:1\.1\">.*?</subsystem>"
           ""))))

(defn isolated-options []
  (let [data-dir (.getCanonicalPath (io/file *isolation-dir*))]
    (mapv (fn [f] (.mkdirs (io/file data-dir f)))
          ["data" "log" "deployments"])
    [(sysprop "jboss.server.log.dir" (format "%s/log" data-dir))
     (sysprop "org.jboss.boot.log.file"
              (format "%s/log/boot.log" data-dir))
     (sysprop "jboss.server.data.dir" (format "%s/data" data-dir))]))

(defn offset-options []
  [(sysprop "jboss.socket.binding.port-offset" "100")])

(defn start-command [modes]
  (let [java-home (System/getProperty "java.home")
        jboss-home (.getCanonicalFile (io/file *home*))]
    (-> [(str java-home "/bin/java")
         "-Xms64m"
         "-Xmx1024m"
         "-XX:MaxPermSize=1024m"
         "-XX:+UseConcMarkSweepGC"
         "-XX:+UseParNewGC"
         "-XX:+CMSClassUnloadingEnabled"
         (debug-options)
         (sysprop "org.jboss.resolver.warning" "true")
         (sysprop "sun.rmi.dgc.client.gcInterval" "3600000")
         (sysprop "logging.configuration"
                  (format "file:%s/standalone/configuration/logging.properties"
                          jboss-home))
         (sysprop "jboss.home.dir" jboss-home)
         (format "-jar %s/jboss-modules.jar" jboss-home)
         (format "-mp %s/modules" jboss-home)
         "-jaxpmodule javax.xml.jaxp-provider"
         "org.jboss.as.standalone"
         "--server-config=standalone-disabled-scanner.xml"]
        (concat
         (if (isolated? modes)
           (isolated-options)
           [(sysprop "org.jboss.boot.log.file"
                     (format "%s/standalone/log/boot.log"
                             jboss-home))])
         (if (offset? modes)
           (offset-options)))
        (as-> x
              (remove nil? x)
              (str/join " " x)))))

(defmacro with-api-endpoint [endpoint & body]
  `(binding [api/*api-endpoint* ~endpoint]
     ~@body))

(defn ready? [endpoint]
  (with-api-endpoint endpoint
    (api/ready?)))

(defn wait-for-ready?
  "Returns true if JBoss is up. Otherwise, sleeps for one second and
   then retries, effectively blocking the current thread until JBoss
   becomes ready or 'attempts' number of seconds has elapsed"
  [endpoint attempts]
  (or (ready? endpoint)
      (when (> attempts 0)
        (Thread/sleep 1000)
        (recur endpoint (dec attempts)))))

(defn start
  "Start up a JBoss, returning a promise that gives you its management url"
  [modes]
  (println "Starting JBoss")
  (if (ready? (default-endpoint modes))
    (throw (Exception. "JBoss is already running!"))
    (let [cmd (start-command modes)
          url (promise)]
      (make-disabled-scanner-conf)
      (println cmd)
      (sh/sh (str/split cmd #" ")
             :line-fn (partial sh/find-management-endpoint
                               #(deliver url %))
             :pump-err? false
             :pump-out? false)
      url)))

(defn stop
  "Shut down whatever JBoss instance is responding to api-url"
  [endpoint]
  (println "Stopping JBoss")
  (with-api-endpoint endpoint
    (api/shutdown)))

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
  ([endpoint content-map]
     (doseq [[name content] content-map]
       (deploy endpoint name content)))
  ([endpoint name content]
     (with-api-endpoint endpoint
       (let [file (if (instance? java.io.File content) content (descriptor name content))
             fname (.getName file)
             url (.toURL file)
             add (api/add fname url)]
         (println "Deploying" (.getCanonicalPath file))
         (when-not (= "success" (:outcome add))
           (api/remove fname)
           (api/add fname url))
         (api/deploy fname)))))

(defn undeploy
  "Undeploy the apps deployed under the given names"
  [endpoint & names]
  (doseq [name names]
    (println "Undeploying" name)
    (with-api-endpoint endpoint
      (api/remove (deployment-name name)))))
