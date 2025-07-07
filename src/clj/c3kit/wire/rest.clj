(ns c3kit.wire.rest
  (:require [c3kit.apron.log :as log]
            [c3kit.apron.util :as util]
            [c3kit.apron.utilc :as utilc]
            [c3kit.wire.api :as api]
            [c3kit.wire.restc :as restc]
            [org.httpkit.client :as client]))

(defn get-async!
  "If callback is not specified, returns a deref-able promise.
   If callback is specified, returns nil."
  [url opts & [callback]]
  (client/get url (restc/-maybe-update-req opts) callback))

(defn get! [url opts]
  @(get-async! url opts))

(defn post-async!
  "If callback is not specified, returns a deref-able promise.
   If callback is specified, returns nil."
  [url opts & [callback]]
  (client/post url (restc/-maybe-update-req opts) callback))

(defn post! [url opts]
  @(post-async! url opts))

(defn put-async!
  "If callback is not specified, returns a deref-able promise.
   If callback is specified, returns nil."
  [url opts & [callback]]
  (client/put url (restc/-maybe-update-req opts) callback))

(defn put! [url opts]
  @(put-async! url opts))

(defn delete-async!
  "If callback is not specified, returns a deref-able promise.
   If callback is specified, returns nil."
  [url opts & [callback]]
  (client/delete url (restc/-maybe-update-req opts) callback))

(defn delete! [url opts]
  @(delete-async! url opts))

(defn default-rest-ex-handler [_request ex]
  (log/error ex)
  (restc/internal-error {:message api/default-error-message}))

(defn wrap-catch-rest-errors [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (if-let [ex-handler (util/config-value (:rest-on-ex @api/config))]
          (ex-handler request e)
          (default-rest-ex-handler request e))))))

(defn- <-json-slurp-try [json-fn v]
  (let [data (slurp v)]
    (try
      (json-fn data)
      (catch Exception e
        (log/error "Couldn't parse as JSON:" data)
        (throw e)))))

(defn- <-json-slurp [v]
  (<-json-slurp-try utilc/<-json v))
(defn- <-json-kw-slurp [v]
  (<-json-slurp-try utilc/<-json-kw v))

(defn wrap-api-json-request [handler & [opts]]
  (fn [request]
    (try
      (let [json-fn (if (:keywords? opts) <-json-kw-slurp <-json-slurp)
            request (restc/-maybe-update-body request json-fn)]
        (handler request))
      (catch Exception _e
        (restc/bad-request)))))

(defn wrap-api-json-response [handler]
  (fn [request]
    (-> (handler request)
        restc/-maybe-update-req)))

(defn wrap-rest [handler & [opts]]
  (-> handler
      wrap-catch-rest-errors
      api/wrap-add-api-version
      wrap-api-json-response
      (wrap-api-json-request opts)))