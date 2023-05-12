(ns c3kit.wire.ajax
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.api :as api]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.js :as cc]
            [cljs-http.client :as http]
            [cljs.core.async :as async]
            [clojure.string :as str]
            [reagent.core :as reagent]))

(declare -do-ajax-request)

(def active-ajax-requests (reagent/atom 0))
(defn activity? [] (not= 0 @active-ajax-requests))

(defn server-down? [{:keys [error-code status]}]
  (and (= :http-error error-code) (#{0 502} status)))

(defn handle-server-down [ajax-call]
  (log/warn "Appears that server is down.  Will retry after in a moment.")
  (flash/add! api/server-down-flash)
  (cc/timeout 3000 #(-do-ajax-request ajax-call)))

(defn handle-unexpected-response [response ajax-call]
  (if-let [on-http-error (:on-http-error (:options ajax-call))]
    (on-http-error response)
    (log/error "Unexpected AJAX response: " response ajax-call)))

(defn handle-unsuccessful-response [response ajax-call]
  (cond (server-down? response) (handle-server-down ajax-call)
        (= 403 (:status response)) (flash/add! api/forbidden-flash)
        :else (handle-unexpected-response response ajax-call)))

(defn triage-response [response ajax-call]
  (cond (= 200 (:status response)) (api/handle-api-response (:body response) ajax-call)
        :else (if-let [handler (:ajax-on-unsuccessful-response @api/config)]
                (handler response ajax-call)
                (handle-unsuccessful-response response ajax-call))))

(defn prep-csrf
  "Create a prep fn to add a CSRF header to each request
  (prep-csrf \"X-CSRF-Token\"] csrf-token)"
  [header token]
  (fn [ajax-call]
    (assoc-in ajax-call [:options :headers header] token)))

(defn params-key [ajax-call]
  (if (#{"GET" "HEAD"} (:method ajax-call))
    :query-params
    (case (-> ajax-call :options :params-type)
      nil :transit-params
      :transit :transit-params
      :query :query-params
      :form :form-params
      :edn :edn-params
      :json :json-params
      :multipart :multipart-params)))

;; Keys used by cljs-http
(def pass-through-keys [:accept
                        :basic-auth
                        :content-type
                        :default-headers
                        :headers
                        :method
                        :oauth-token
                        :transit-opts
                        :with-credentials?])

(defn request-map [ajax-call]
  (let [prep (or (:ajax-prep-fn @api/config) identity)
        {:keys [options params] :as ajax-call} (prep ajax-call)]
    (cond-> (select-keys options pass-through-keys)
            params (assoc (params-key ajax-call) params))))

(defn -do-ajax-request [{:keys [method method-fn url params] :as ajax-call}]
  (log/debug "<" method url params)
  (go
    (swap! active-ajax-requests inc)
    (let [request (request-map ajax-call)
          {:keys [error-code status body] :as response} (async/<! (method-fn url request))]
      (log/debug ">" method url error-code status (:status body))
      (triage-response response ajax-call)
      (swap! active-ajax-requests dec))))

(defn build-ajax-call [method method-fn url params handler opt-args]
  {:options   (ccc/->options opt-args)
   :method    method
   :method-fn method-fn
   :url       url
   :params    params
   :handler   handler})

(defn -method-parts [method]
  (case method
    :get ["GET" http/get]
    :post ["POST" http/post]))

;; MDM - get! post! request!
;; These functions initiate ajax calls to the server and conform to a semi-formal API.
;; Requests are simple: get or post to a URL.
;; Responses from the server are a map described by the response-schema above.
;; Every call to do-get or do-post must include a handler function that takes one argument, the response :payload.  It
;; gets called when a response has :status of :ok. The :payload can be anything.  Client code should know what data
;; to expect based on what it's asking for.
;;
;; Options:    - extensible
;;  *** - see c3kit.wire.api for list of general API options
;;  *** - see pass-through-keys for a list of cljs-http optional keys
;;  :on-http-error  - (fn [response]...) invoked when the HTTP status code is unexpected
;;  :params-type - specify the delivery format for params :transit (default)|:query|:form|:edn|:json|:multipart

(defn get! [url params handler & opt-args]
  (-do-ajax-request (build-ajax-call "GET" http/get url params handler opt-args)))

(defn post! [url params handler & opt-args]
  (-do-ajax-request (build-ajax-call "POST" http/post url params handler opt-args)))

(defn request! [method url params handler & opt-args]
  (let [method-name (str/upper-case (name method))
        method-fn (fn [url & [req]] (http/request (merge req {:method method :url url})))]
    (-do-ajax-request (build-ajax-call method-name method-fn url params handler opt-args))))
