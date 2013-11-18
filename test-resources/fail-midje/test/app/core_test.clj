(ns app.core-test
  (:use midje.sweet))

(fact "truth is" false => true)

(fact "truth is exceptional" (throw (Exception.)) => true)

