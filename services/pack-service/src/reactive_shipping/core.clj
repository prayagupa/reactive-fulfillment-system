(ns reactive-shipping.core
  (:gen-class)
  (:require [clojure.core.async :as async]
            [reactive-shipping.shipping :as shipping]))

(def stdin-reader
  (java.io.BufferedReader. *in*))

(defn println-err [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn -main
  "shipping-service"
  [& args]
  (let [[mode num-times] args]

    (println-err args)

    (case mode

      "default-reactive"
      (shipping/read-and-ship "input-events")

      "inline-reactive"
      ;; Read each package from stdin
      (doseq [package (line-seq stdin-reader)]
        (let [event (shipping/ship-it package)]
          (println event))))))
