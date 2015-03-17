(defproject org.immutant/fntest "2.0.3-SNAPSHOT"
  :description "A harness for running Immutant integration tests"
  :url "https://github.com/immutant/fntest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[jboss-as-management "0.4.0"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.immutant/deploy-tools "2.0.0"]
                 [backtick "0.1.0"]
                 [bultitude "0.2.6"]]
  :profiles {:dev
             {:dependencies [[org.clojure/clojure "1.5.1"]]}}
  :signing {:gpg-key "BFC757F9"}
  :deploy-repositories {"releases" :clojars})
