(ns c3kit.wire.rest
  (:require [c3kit.wire.restc :as restc]
            [cljs-http.client :as client]
            [cljs.core.async :refer-macros [go]]
            [cljs.core.async :as async]
            [reagent.core :as reagent]))

(def active-reqs (reagent/atom 0))
(defn activity? [] (not= 0 @active-reqs))

(defn- async-callback! [channel callback]
  ;; [GMJ] `go` block is not tested because I can't get it to work with speclj
  (go
    (swap! active-reqs inc)
    (callback (async/<! channel))
    (swap! active-reqs dec))
  nil)

(defn request! [method url request callback]
  (let [channel (method url (restc/-maybe-update-req request))]
    (async-callback! channel callback)))

(defn get! [url request callback]
  (request! client/get url request callback))

(defn post! [url request callback]
  (request! client/post url request callback))

(defn put! [url request callback]
  (request! client/put url request callback))

(defn delete! [url request callback]
  (request! client/delete url request callback))