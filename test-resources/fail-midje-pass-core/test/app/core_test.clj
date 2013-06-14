(ns app.core-test
  (:use midje.sweet
        clojure.test))

(fact "truth is" false => true)

(deftest truth
  (is (not false)))
