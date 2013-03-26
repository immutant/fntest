(defproject org.immutant/fntest "0.3.11"
  :description "A harness for running Immutant integration tests"
  :url "https://github.com/immutant/fntest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :exclusions [org.clojure/clojure]
  :dependencies [[jboss-as-management "0.2.1"]
                 [org.clojure/tools.nrepl "0.2.1"]
                 [bultitude "0.2.0"]]
  :lein-release {:deploy-via :clojars})
