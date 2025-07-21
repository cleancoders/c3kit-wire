(ns c3kit.wire.rest
  (:require [c3kit.wire.restc :as restc]
            [cljs-http.client :as client]
            [c3kit.wire.api :as api]
            [cljs.core.async :refer-macros [go]]
            [cljs.core.async :as async]))

(defn- async-callback! [channel callback]
  ;; [GMJ] `go` block is not tested because I can't get it to work with speclj
  (go
    (callback (async/<! channel)))
  nil)

(defn- prep [request]
  (let [prep-fn (or (:rest-prep-fn @api/config) identity)]
    (prep-fn request)))

(defn request! [method url request]
  ;; [GMJ] had to extract this to make specs pass with advanced optimization for some reason...
  (method url (restc/-maybe-update-req (prep request))))

(defn get! [url request callback]
  (let [channel (request! client/get url request)]
    (async-callback! channel callback)))

(defn post! [url request callback]
  (let [channel (request! client/post url request)]
    (async-callback! channel callback)))

(defn put! [url request callback]
  (let [channel (request! client/put url request)]
    (async-callback! channel callback)))

(defn delete! [url request callback]
  (let [channel (request! client/delete url request)]
    (async-callback! channel callback)))