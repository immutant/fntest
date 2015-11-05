(defproject org.immutant/fntest "2.0.8"
  :description "A harness for running Immutant integration tests"
  :url "https://github.com/immutant/fntest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[jboss-as-management "0.4.2"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.immutant/deploy-tools "2.1.0"]
                 [backtick "0.1.0"]
                 [bultitude "0.2.6"]]
  :profiles {:dev
             {:dependencies [[org.clojure/clojure "1.5.1"]
                             [leiningen-core "2.5.3"]]}
             :base
             {:dependencies ^:replace []}}
  :signing {:gpg-key "BFC757F9"}
  :deploy-repositories {"releases" :clojars})
