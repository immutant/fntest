(ns app.core-test
  (:use midje.sweet
        clojure.test))

(fact "truth is" true => true)

(deftest truth
  (is (not false)))
