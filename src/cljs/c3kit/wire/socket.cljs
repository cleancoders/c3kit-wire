(ns c3kit.wire.socket
  (:require [c3kit.apron.corec :as ccc]))

(defn ready-state [ws] (ccc/oget ws "readyState"))
(defn send! [ws data] (js-invoke ws "send" data))

(defn close!
  ([ws] (js-invoke ws "close"))
  ([ws code] (js-invoke ws "close" code))
  ([ws code reason] (js-invoke ws "close" code reason)))

(defn connecting? [ws] (= 0 (ready-state ws)))
(defn open? [ws] (= 1 (ready-state ws)))
(defn closing? [ws] (= 2 (ready-state ws)))
(defn closed? [ws] (= 3 (ready-state ws)))
