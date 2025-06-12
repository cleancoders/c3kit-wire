(ns c3kit.wire.rest
  (:require [c3kit.apron.log :as log]
            [c3kit.apron.util :as util]
            [c3kit.apron.utilc :as utilc]
            [c3kit.wire.api :as api]
            [c3kit.wire.restc :as restc]
            [org.httpkit.client :as client]
            [ring.util.response :as response]))

(defn- maybe-update-body [{:keys [body] :as request} f]
  (cond-> request
          body (update :body f)))

(defn- maybe-update-content-type [{:keys [headers body] :as request}]
  (cond-> request
          (and body (not (get headers "Content-Type")))
          (response/content-type "application/json")))

(defn- maybe-update-opts [opts]
  (-> opts
      (maybe-update-body utilc/->json)
      maybe-update-content-type))

(defn get-async! [url opts callback]
  (client/get url (maybe-update-opts opts) callback))

(defn get! [url opts callback]
  @(get-async! url opts callback))

(defn post-async! [url opts callback]
  (client/post url (maybe-update-opts opts) callback))

(defn post! [url opts callback]
  @(post-async! url opts callback))

(defn put-async! [url opts callback]
  (client/put url (maybe-update-opts opts) callback))

(defn put! [url opts callback]
  @(put-async! url opts callback))

(defn default-rest-ex-handler [_request ex]
  (log/error ex)
  (restc/internal-error {:message "Our apologies. An error occurred and we have been notified."}))

(defn wrap-catch-api-errors [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (if-let [ex-handler (util/config-value (:rest-on-ex @api/config))]
          (ex-handler request e)
          (default-rest-ex-handler request e))))))

(defn wrap-api-json-request [handler & [opts]]
  (fn [request]
    (handler (maybe-update-body request (if (:key-words? opts)
                                          utilc/<-json-kw
                                          utilc/<-json)))))

(defn wrap-api-json-response [handler]
  (fn [request]
    (-> (handler request)
        (maybe-update-body utilc/->json)
        maybe-update-content-type)))

(defn wrap-rest [handler & [opts]]
  (-> handler
      wrap-catch-api-errors
      api/wrap-add-api-version
      wrap-api-json-response
      (wrap-api-json-request opts)))