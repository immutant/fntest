(defproject app "0.0.1-SNAPSHOT"
  :description "Cool new project to do things and stuff"
  :repositories [["Immutant incremental builds"
                  "http://downloads.immutant.org/incremental/"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.immutant/web "2.x.incremental.669"]]
  :profiles {:dev {:dependencies [[expectations "2.0.6"]
                                  [org.clojure/tools.nrepl "0.2.7"]]}})
