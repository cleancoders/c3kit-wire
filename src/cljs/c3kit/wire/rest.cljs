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

(defn payload [opts response]
  (if (:rest/unwrap-body? opts)
    (:body response)
    response))

(defn wrap-success-handler [handler]
  (fn [opts response]
    (when (success? response)
      (handler opts (payload opts response)))))

(defn wrap-response-code [status f handler]
  (fn [opts response]
    (if (= status (:status response))
      (f (payload opts response))
      (handler opts response))))

(defn wrap-response-codes [spec handler]
  (reduce-kv
    (fn [acc k v]
      (wrap-response-code k v acc))
    handler
    spec))

(defn wrap-user-handlers [handler]
  (fn [opts response]
    (if-let [callback (get-in opts [:rest/handlers (:status response)])]
      (callback (payload opts response))
      (handler opts response))))

(defn wrap-form-errors [handler]
  (fn [opts response]
    (let [ratom (:rest/form-ratom opts)
          errors (:errors (:body response))]
      (when (and ratom errors)
        (swap! ratom assoc :errors errors :display-errors? true)))
    (handler opts response)))

(defn with-handlers [& opts]
  (let [spec (ccc/->options opts)]
    {:rest/handlers spec}))

(defn wrap-handler [middleware handler opts]
  (letfn [(callback [_opts response]
            (handler response))]
    (partial (middleware callback) opts)))

(def active-reqs (reagent/atom 0))
(defn activity? [] (not= 0 @active-reqs))

(defn -request! [channel callback]
  (go
    (swap! active-reqs inc)
    (callback (async/<! channel))
    (swap! active-reqs dec))
  nil)

(defn request! [method url request handler options]
  (let [opts       (merge @api/config (ccc/->options options))
        middleware (:rest/response-middleware opts)
        callback   (if middleware (wrap-handler middleware handler opts) handler)
        channel    (method url (restc/-maybe-update-req request))]
    (-request! channel callback)))

(defn get! [url request callback & opts]
  (request! client/get url request callback opts))

(defn post! [url request callback & opts]
  (request! client/post url request callback opts))

(defn put! [url request callback & opts]
  (request! client/put url request callback opts))

(defn delete! [url request callback & opts]
  (request! client/delete url request callback opts))