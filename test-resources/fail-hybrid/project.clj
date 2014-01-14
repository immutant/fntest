(defproject app "0.0.1-SNAPSHOT"
  :description "Cool new project to do things and stuff"
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :profiles {:dev {:dependencies [[midje "1.5.0"]]}}
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
  
