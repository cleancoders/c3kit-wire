(ns c3kit.wire.rest
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.wire.api :as api]
            [c3kit.wire.restc :as restc]
            [cljs-http.client :as client]
            [cljs.core.async :refer-macros [go]]
            [cljs.core.async :as async]
            [reagent.core :as reagent]))

(defn configure! [& options]
  (swap! api/config merge (ccc/->options options)))

(def active-reqs (reagent/atom 0))
(defn activity? [] (not= 0 @active-reqs))

(defn -request! [channel callback]
  (go
    (swap! active-reqs inc)
    (callback (async/<! channel))
    (swap! active-reqs dec))
  nil)

(defn request! [method url request callback]
  (let [channel  (method url (restc/-maybe-update-req request))
        wrapper  (:rest/wrap-response-fn @api/config)
        callback (cond-> callback wrapper (comp wrapper))]
    (-request! channel callback)))

(defn get! [url request callback]
  (request! client/get url request callback))

(defn post! [url request callback]
  (request! client/post url request callback))

(defn put! [url request callback]
  (request! client/put url request callback))

(defn delete! [url request callback]
  (request! client/delete url request callback))