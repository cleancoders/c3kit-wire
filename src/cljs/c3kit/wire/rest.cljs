(ns c3kit.wire.rest
  (:require [c3kit.wire.restc :as restc]
            [cljs-http.client :as client]
            [cljs.core.async :refer-macros [go]]
            [cljs.core.async :as async]))

(defn- async-callback! [channel callback]
  ;; [GMJ] `go` block is not tested because I can't get it to work with speclj
  (go
    (callback (async/<! channel)))
  nil)

(defn get! [url req callback]
  (let [channel (client/get url (restc/-maybe-update-req req))]
    (async-callback! channel callback)))

(defn post! [url req callback]
  (let [channel (client/post url (restc/-maybe-update-req req))]
    (async-callback! channel callback)))

(defn put! [url req callback]
  (let [channel (client/put url (restc/-maybe-update-req req))]
    (async-callback! channel callback)))