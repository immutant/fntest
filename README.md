# fntest

Used internally by both the `test` task of the
[lein-immutant plugin](https://github.com/immutant/lein-immutant) and
Immutant's own integration tests, this library enables you to test
against your application while deployed on
[Immutant](http://immutant.org) or even run your tests within the
deployed application itself. Here's an example:

```clojure
    (ns your.test-code
      (:use clojure.test)
      (:require [clj-http.client :as http]
                [fntest.core     :as fnt]))
    
    ;;; Run Immutant and deploy our application
    (use-fixtures :once
      (compose-fixtures
        fnt/with-jboss
        (fnt/with-deployment *file* {:root "./", :context-path "/foo"})))

    ;;; Run browser tests against the deployed app
    (deftest remote-http-test
      (let [result (http/get (format "http://localhost:%d/foo" (fnt/offset-port :http)))]
        (is (.contains (:body result) "Howdy!"))))
        
    ;;; Alternatively, deploy and run all tests in one shot, but be
    ;;; careful not to invoke test-in-container inside the container!
    (deftest run-all-tests-inside-immutant
      (is (fnt/test-in-container "some-name" "./" :dirs ["container"])))
```

[Midje](https://github.com/marick/Midje), [Expectations](https://github.com/jaycfields/expectations)
 and `clojure.test` tests are supported by `fntest.core/test-in-container`. Which tests are
 run depends on which testing library is found on the classpath. If Midje is present, then both
 Midje and `clojure.test` tests are run via the Midje test runner.  If Midje is not found, but
 Expectations is present, then only the Exepctations tests are run. If neither Midje nor
 Expectations is found, the standard `clojure.test` test runner is used.
 
