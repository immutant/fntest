(defproject org.immutant/fntest "0.3.6"
  :description "A harness for running Immutant integration tests"
  :url "https://github.com/immutant/fntest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[jboss-as-management "0.2.0"]
                 [org.clojure/tools.nrepl "0.2.1"]]
  :lein-release {:deploy-via :clojars})
