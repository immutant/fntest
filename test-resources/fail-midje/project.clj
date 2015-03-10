(defproject app "0.0.1-SNAPSHOT"
  :description "Cool new project to do things and stuff"
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :profiles {:dev {:dependencies [[midje "1.5.0"]
                                  [org.immutant/immutant "2.0.0-beta2"]
                                  [org.clojure/tools.nrepl "0.2.7"]]}})
