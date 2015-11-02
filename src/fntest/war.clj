;; Copyright 2008-2015 Red Hat, Inc, and individual contributors.
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

(ns fntest.war
  (:require [immutant.deploy-tools.war :as war]
            [clojure.java.io           :as io]
            [fntest.util :refer (status)])
  (:import java.io.File))

(defn enable-dev [project]
  (assoc project :dev? true))

(defn enable-nrepl [project port-file]
  (if port-file
    (update-in project [:nrepl] merge
      {:start? true
       :port 0
       :port-file (.getAbsolutePath port-file)})
    project))

(defn set-classpath [project]
  (require 'leiningen.core.classpath)
  (assoc project :classpath
    ((resolve 'leiningen.core.classpath/get-classpath) project)))

(defn dependency-hierarchy [project]
  (require 'leiningen.core.classpath)
  ((resolve 'leiningen.core.classpath/dependency-hierarchy) :dependencies project))

(defn resolve-dependencies [project]
  (require 'leiningen.core.classpath)
  ((resolve 'leiningen.core.classpath/resolve-dependencies) :dependencies project))

(defn set-init [project]
  (if (:main project)
    (assoc project :init-fn (symbol (str (:main project)) "-main"))
    project))

(defn set-resources [project]
  (assoc project :war-resource-paths (get-in project [:immutant :war :resource-paths])))

(defn project->war
  [root & {:keys [port-file profiles]}]
  (require 'leiningen.core.project)
  (let [read-fn (resolve 'leiningen.core.project/read)
        project (read-fn
                  (.getAbsolutePath (io/file root "project.clj"))
                  (or profiles [:dev :test]))]
    (status "Creating war file"
      (war/create-war
        (File/createTempFile "fntest" ".war")
        (-> project
          (assoc
            :dependency-hierarcher dependency-hierarchy
            :dependency-resolver resolve-dependencies)
          set-classpath
          set-init
          enable-dev
          set-resources
          (enable-nrepl port-file))))))
