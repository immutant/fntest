(defproject app "0.0.1-SNAPSHOT"
  :description "Cool new project to do things and stuff"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.immutant/web "2.0.0-beta2"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]
                                  [org.clojure/tools.nrepl "0.2.7"]]}}
  :repl-options {:nrepl-middleware [(fn [h]
                                      (fn [{:keys [code transport] :as args}]
                                        (if code (.println System/out code))
                                        (h (assoc args :transport
                                                  (reify clojure.tools.nrepl.transport/Transport
                                                    (send [this resp]
                                                      (if-let [out (:out resp)]
                                                        (.print System/out out))
                                                      (.send transport resp)
                                                      this))))))]})
  
