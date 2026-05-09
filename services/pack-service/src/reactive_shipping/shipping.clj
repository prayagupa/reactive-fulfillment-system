(ns reactive-shipping.shipping
    (:gen-class)
    (:require [clojure.core.async :as async]))

(def stdin-reader
  (java.io.BufferedReader. *in*))

(def in-stream (async/chan))
(def out-stream (async/chan))

(defn ship-it
  "ship my package"
  [package]
  ;; take some to label each package and pass the package
  (Thread/sleep 1000)
  (str package " [consumer confirms] shipment"))

(defn shipping-consumers
  "Start num-consumers threads that will consume work
  from the in-chan and put it into the out-chan."
  [num-consumers]
  (dotimes [_ num-consumers]
    (async/thread
     (while true
            (let [package (async/<!! in-stream)
                  after-shipment (ship-it package)]
              (async/>!! out-stream after-shipment))))))

(defn shipping-aggregator
  "Take items from the out-stream and shows on the board."
  []
  (async/thread
   (while true
          (let [event (async/<!! out-stream)]
            (println (str "[aggregator] " event))))))

(defn read-and-ship [input-events]
  (do
    (shipping-consumers 8)
    (shipping-aggregator)
    (doseq [package (line-seq stdin-reader)]
      (async/>!! in-stream package))))
