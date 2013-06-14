# fntest

Used internally by both the lein-immutant plugin and Immutant's own
integration tests, this library enables you to test against your
application while deployed on Immutant or even run your tests within
the deployed application itself.  Here's an example:

```clojure
    (ns your.test-code
      (:use clojure.test)
      (:require [clj-http.client :as http]
                [fntest.core     :as fntest))
    
    ;;; Fire up Immutant
    (use-fixtures :once fntest/with-jboss)
    
    ;;; Deploy our application
    (use-fixtures :once (fntest/with-deployment *file* {:root "./", :context-path "/foo"}))
    
    ;;; Run browser tests against the deployed app
    (deftest remote-http-test
      (let [result (http/get "http://localhost:8080/foo)]
        (is (.contains (:body result) "Howdy!"))))
        
    ;;; Alternatively, deploy and run everything in one shot
    (deftest run-all-tests-inside-immutant
      (is (fntest/test-in-container "some-name" "./")))
```

Both Midje and clojure.test tests are supported by
fntest.core/test-in-container.
