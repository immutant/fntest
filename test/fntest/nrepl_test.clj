(ns fntest.nrepl-test
  (:use clojure.test
        fntest.core))

(use-fixtures :once with-jboss)

(deftest verify-success-core-tests
  (is (test-in-container "pass-core" "test-resources/pass-core")))

(deftest verify-failed-core-tests
  (is (not (test-in-container "fail-core" "test-resources/fail-core"))))

(deftest verify-success-midje-tests
  (is (test-in-container "pass-midje" "test-resources/pass-midje")))

(deftest verify-failed-midje-tests
  (is (not (test-in-container "fail-midje" "test-resources/fail-midje"))))

(deftest verify-success-hybrid-tests
  (is (test-in-container "pass-hybrid" "test-resources/pass-hybrid")))

(deftest verify-failed-hybrid-tests
  (is (not (test-in-container "fail-hybrid" "test-resources/fail-hybrid"))))

(deftest verify-only-midje-failed
  (is (not (test-in-container "fail-hybrid" "test-resources/fail-midje-pass-core"))))

(deftest verify-only-core-failed
  (is (not (test-in-container "fail-hybrid" "test-resources/fail-core-pass-midje"))))
