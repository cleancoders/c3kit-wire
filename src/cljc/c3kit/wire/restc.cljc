(ns c3kit.wire.restc
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.utilc :as utilc]
            [clojure.string :as str]))

(defn response
  ([status]
   {:status  status
    :headers {}})
  ([status body]
   (assoc (response status) :body body))
  ([status body headers]
   (assoc (response status body) :headers headers)))

(defn ok
  ([] (response 200))
  ([body] (response 200 body))
  ([body headers] (response 200 body headers)))

(defn created
  ([] (response 201))
  ([body] (response 201 body))
  ([body headers] (response 201 body headers)))

(defn accepted
  ([] (response 202))
  ([body] (response 202 body))
  ([body headers] (response 202 body headers)))

(defn non-authoritative
  ([] (response 203))
  ([body] (response 203 body))
  ([body headers] (response 203 body headers)))

(defn no-content
  ([] (response 204))
  ([headers] (response 204 nil headers)))

(defn not-modified
  ([] (response 304))
  ([headers] (response 304 nil headers)))

(defn bad-request
  ([] (response 400))
  ([body] (response 400 body))
  ([body headers] (response 400 body headers)))

(defn unauthorized
  ([] (response 401))
  ([body] (response 401 body))
  ([body headers] (response 401 body headers))
  ([body headers www-authenticate]
   (response 401 body (assoc headers "WWW-Authenticate" www-authenticate))))

(defn payment-required
  ([] (response 402))
  ([body] (response 402 body))
  ([body headers] (response 402 body headers)))

(defn forbidden
  ([] (response 403))
  ([body] (response 403 body))
  ([body headers] (response 403 body headers)))

(defn not-found
  ([] (response 404))
  ([body] (response 404 body))
  ([body headers] (response 404 body headers)))

(defn conflict
  ([] (response 409))
  ([body] (response 409 body))
  ([body headers] (response 409 body headers)))

(defn internal-error
  ([] (response 500))
  ([body] (response 500 body))
  ([body headers] (response 500 body headers)))

(defn not-found-handler [request]
  (not-found (:uri request)))

(defn status [response] (:status response))
(defn success? [response]
  (let [status (status response)]
    (and (< status 300)
         (>= status 200))))
(defn error? [response] (>= (status response) 400))

(defn body [response] (:body response))
(defn <-json-body [response] (utilc/<-json (:body response)))
(defn <-json-kw-body [response] (utilc/<-json-kw (:body response)))

(defn headers [response] (:headers response))

(defn -maybe-update-body [{:keys [body] :as request} f]
  (cond-> request
          body (update :body f)))

(defn ->cookies-str [cookies]
  (->> cookies
       (map #(list (name (first %)) (:value (second %))))
       (map #(str (first %) "=" (second %)))
       (str/join ";")))

; TODO - append cookies if present already
(defn maybe-attach-cookies [{:keys [cookies] :as request}]
  (cond-> request
          (seq cookies) (-> (assoc-in [:headers "Cookie"] (->cookies-str cookies))
                            (dissoc :cookies))))

(defn -maybe-update-content-type [{:keys [headers body] :as request}]
  (cond-> request
          (and body (not (get headers "Content-Type")))
          (assoc-in [:headers "Content-Type"] "application/json")))

(defn -maybe-update-req [req]
  (-> req
      (-maybe-update-body utilc/->json)
      maybe-attach-cookies
      -maybe-update-content-type))