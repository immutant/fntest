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

(ns fntest.util)

(def ^:dynamic *output-fns*
  {:error #(binding [*out* *err*]
             (print "Error:" %))
   :warn #(binding [*out* *err*]
             (print "Warning:" %))
   :info print})

(defn error [msg]
  ((:error *output-fns*) msg))

(defn warn [msg]
  ((:warn *output-fns*) msg))

(defn info [msg]
  ((:info *output-fns*) msg))

(defmacro status [msg cmd]
  `(do
     (info (str ~msg "... "))
     (flush)
     (let [v# ~cmd]
       (info "done!\n")
       v#)))
