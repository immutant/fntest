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
            [jboss-as.management :as api]
            [clojure.string      :as str]
            [fntest.util :refer (status)]))

(def ^:dynamic *home*
  (or (System/getenv "WILDFLY_HOME")
    (System/getenv "JBOSS_HOME")))
(def ^:dynamic *isolation-dir* "target/isolated-wildfly")
(def ^:dynamic *port-offset* 67)
(def ^:dynamic *log-level* :INFO)

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

(defn change-cluster-password [config]
  (str/replace config
    #"(<cluster-password>)[^<]*"
    "$1fntest"))

(defn set-main-server-group-full-ha [config]
  (-> config
    (str/replace
      #"(main-server-group\" profile=\"full)\""
      "$1-ha\"")
    (str/replace
      #"(ref=\"full)(-sockets\")"
      "$1-ha$2")))

(defn disable-management-security [config]
  (str/replace config
    #"(<http-interface )(security-realm=['\"]ManagementRealm['\"])"
    "$1"))

(defn enable-domain-port-offset [config]
  (when-not (re-find #"port-offset:0" config)
    (str/replace config #"(<server name=\"server-one\" group=\"main-server-group\">)"
      "$1\n<socket-bindings port-offset=\"\\${jboss.socket.binding.port-offset:0}\"/>")))

(defn create-server [modes]
  (let [result (api/create-server :domain (domain? modes)
                 :offset (if (offset? modes) *port-offset* 0)
                 :jboss-home *home*
                 :log-level *log-level*
                 :base-dir (isolated-base-dir modes *home*)
                 :debug (debug? modes))]
    (when (isolated? modes)
      (if (domain? modes)
        (do
          (api/alter-config! result (comp change-cluster-password set-main-server-group-full-ha))
          (api/alter-config! result (comp enable-domain-port-offset disable-management-security) "host.xml"))
        (api/alter-config! result disable-management-security)))
    result))

(defn deploy
  "Deploy with the given deployment name and war file."
  ([server war-map]
     (doseq [[name war] war-map]
       (deploy server name war)))
  ([server name war]
     (let [file (io/file war)]
       (status (format "Deploying %s as %s" (.getCanonicalPath file) name)
         (api/deploy server name (.toURL file))))))

(defn undeploy
  "Undeploy the apps deployed under the given names"
  [server & names]
  (doseq [name names]
    (status (str "Undeploying " name)
      (api/undeploy server name))))
