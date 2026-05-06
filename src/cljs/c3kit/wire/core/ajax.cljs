(ns c3kit.wire.core.ajax
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.api :as api]
            [c3kit.wire.js :as cc]
            [c3kit.wire.restc :as restc]
            [cljs-http.client :as http]
            [cljs.core.async :as async]
            [clojure.string :as str]))

(declare -do-ajax-request)

(defn make-state
  ([] (make-state cljs.core/atom))
  ([atom-fn] {:active-requests (atom-fn 0)}))

(defn server-down? [{:keys [error-code status]}]
  (and (= :http-error error-code) (#{0 502} status)))

(defn handle-server-down [state ajax-call]
  (log/warn "Appears that server is down.  Will retry after in a moment.")
  ((:flash-add! @api/config) api/server-down-flash)
  (cc/timeout 3000 #(-do-ajax-request state ajax-call)))

(defn handle-unexpected-response [response ajax-call]
  (if-let [on-http-error (:on-http-error (:options ajax-call))]
    (on-http-error response)
    (log/error "Unexpected AJAX response: " response ajax-call)))

(defn handle-unsuccessful-response [state response ajax-call]
  (cond (server-down? response) (handle-server-down state ajax-call)
        (= 403 (:status response)) ((:flash-add! @api/config) api/forbidden-flash)
        :else (handle-unexpected-response response ajax-call)))

(defn triage-response [state response ajax-call]
  (cond (restc/success? response) (api/handle-api-response (:body response) ajax-call)
        :else (if-let [handler (:ajax-on-unsuccessful-response @api/config)]
                (handler response ajax-call)
                (handle-unsuccessful-response state response ajax-call))))

(defn prep-csrf [header token]
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

(defn -do-ajax-request [state {:keys [method method-fn url params] :as ajax-call}]
  (log/debug "<" method url params)
  (go
    (swap! (:active-requests state) inc)
    (let [request (request-map ajax-call)
          {:keys [error-code status body] :as response} (async/<! (method-fn url request))]
      (log/debug ">" method url error-code status (:status body))
      (triage-response state response ajax-call)
      (swap! (:active-requests state) dec))))

(defn build-ajax-call [method method-fn url params handler opt-args]
  {:options   (ccc/->options opt-args)
   :method    method
   :method-fn method-fn
   :url       url
   :params    params
   :handler   handler})

(defn do-get! [state url params handler & opt-args]
  (-do-ajax-request state (build-ajax-call "GET" http/get url params handler opt-args)))

(defn do-post! [state url params handler & opt-args]
  (-do-ajax-request state (build-ajax-call "POST" http/post url params handler opt-args)))

(defn do-request! [state method url params handler & opt-args]
  (let [method-name (str/upper-case (name method))
        method-fn (fn [url & [req]] (http/request (merge req {:method method :url url})))]
    (-do-ajax-request state (build-ajax-call method-name method-fn url params handler opt-args))))

(defonce default-state (make-state))
(def active-ajax-requests (:active-requests default-state))
(defn activity? [] (not= 0 @active-ajax-requests))

(defn get!     [url params handler & opts]   (apply do-get!     default-state url params handler opts))
(defn post!    [url params handler & opts]   (apply do-post!    default-state url params handler opts))
(defn request! [m url params handler & opts] (apply do-request! default-state m url params handler opts))
