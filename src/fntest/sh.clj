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

(ns fntest.sh
  (:require [clojure.java.io :as io]))

(def ^:dynamic *pump-should-sleep* true)

(defn- pump
  [reader out line-fn pump?]
  (loop [line (.readLine reader)]
    (when line
      (let [line (line-fn line)]
        (when pump? 
          (.write out (str line "\n"))
          (.flush out)))
      (when *pump-should-sleep*
        (Thread/sleep 10))
      (recur (.readLine reader)))))

(defn find-management-endpoint [f line]
  (if-let [match (re-find #"Http management interface listening on (.*/management)" line)]
    (f (last match)))
  line)

(defn sh
  "A version of clojure.java.shell/sh that streams out/err, and returns the process created.
It also allows applying a fn to each line of the output. Borrowed from
leiningen.core/eval and modified."
  [cmd & {:keys [line-fn pump-err? pump-out?]
          :or {line-fn identity
               pump-err? true
               pump-out? true}}]
  (let [proc (.exec (Runtime/getRuntime)
                    (into-array cmd))]
    (.start
     (Thread.
      (bound-fn []
        (with-open [out (io/reader (.getInputStream proc))
                    err (io/reader (.getErrorStream proc))]
          (doto (Thread. (bound-fn []
                           (pump out *out* line-fn pump-out?)))
            .start
            .join)
          (doto (Thread. (bound-fn []
                           (pump err *err* line-fn pump-err?)))
            .start
            .join)))))
    proc))
