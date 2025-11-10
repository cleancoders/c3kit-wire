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

(defn success? [response] (<= 200 (:status response) 299))
(defn error? [response] (<= 400 (:status response) 600))
(defn bad-req? [response] (= 400 (:status response)))
(defn unauthenticated? [response] (= 401 (:status response)))
(defn unauthorized? [response] (= 403 (:status response)))
(defn not-found? [response] (= 404 (:status response)))
(defn server-error? [response] (<= 500 (:status response)))

(def active-reqs (reagent/atom 0))
(defn activity? [] (not= 0 @active-reqs))

(defn -request! [channel callback]
  (go
    (swap! active-reqs inc)
    (callback (async/<! channel))
    (swap! active-reqs dec))
  nil)

(defn request! [method url request callback]
  (let [channel (method url (restc/-maybe-update-req request))]
    (-request! channel callback)))

(defn get! [url request callback]
  (request! client/get url request callback))

(defn post! [url request callback]
  (request! client/post url request callback))

(defn put! [url request callback]
  (request! client/put url request callback))

(defn delete! [url request callback]
  (request! client/delete url request callback))