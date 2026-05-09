(ns reactive-shipping.core-test
  (:require [clojure.test :refer :all]
            [reactive-shipping.core :refer :all]))

(deftest should-process-packages
  (testing "should process packages"
    (read-and-ship "input-events")
    (is (= 0 1))))
