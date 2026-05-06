(ns c3kit.wire.core.rest
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.wire.api :as api]
            [c3kit.wire.restc :as restc]
            [cljs-http.client :as client]
            [cljs.core.async :refer-macros [go]]
            [cljs.core.async :as async]))

(defn make-state
  ([] (make-state cljs.core/atom))
  ([atom-fn] {:active-requests (atom-fn 0)}))

(defn configure! [& options]
  (swap! api/config merge (ccc/->options options)))

(def  success?         restc/success?)
(defn error?           [response] (<= 400 (:status response) 600))
(defn bad-req?         [response] (= 400 (:status response)))
(defn unauthenticated? [response] (= 401 (:status response)))
(defn unauthorized?    [response] (= 403 (:status response)))
(defn not-found?       [response] (= 404 (:status response)))
(defn server-error?    [response] (<= 500 (:status response)))

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

(defn -request! [state channel callback]
  (go
    (swap! (:active-requests state) inc)
    (callback (async/<! channel))
    (swap! (:active-requests state) dec))
  nil)

(defn request! [state method url request handler options]
  (let [opts       (merge @api/config (ccc/->options options))
        middleware (:rest/response-middleware opts)
        callback   (if middleware (wrap-handler middleware handler opts) handler)
        channel    (method url (restc/-maybe-update-req request))]
    (-request! state channel callback)))

(defn do-get!    [state url request callback & opts] (request! state client/get    url request callback opts))
(defn do-post!   [state url request callback & opts] (request! state client/post   url request callback opts))
(defn do-put!    [state url request callback & opts] (request! state client/put    url request callback opts))
(defn do-delete! [state url request callback & opts] (request! state client/delete url request callback opts))

(defonce default-state (make-state))
(def active-reqs (:active-requests default-state))
(defn activity? [] (not= 0 @active-reqs))

(defn get!    [url request callback & opts] (apply do-get!    default-state url request callback opts))
(defn post!   [url request callback & opts] (apply do-post!   default-state url request callback opts))
(defn put!    [url request callback & opts] (apply do-put!    default-state url request callback opts))
(defn delete! [url request callback & opts] (apply do-delete! default-state url request callback opts))
