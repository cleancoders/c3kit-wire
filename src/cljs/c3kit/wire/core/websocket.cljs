(ns c3kit.wire.core.websocket
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.api :as api]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.websocketc :as wsc]
            [clojure.string :as str]))

(defn make-state
  ([] (make-state cljs.core/atom))
  ([atom-fn] {:open?         (atom-fn false)
              :reconnection? (atom false)
              :pending-calls (atom [])}))

(def client nil)

(declare connect!)

(defn handle-remote-response [remote-call response]
  (log/debug "remote response: " response)
  (api/handle-api-response response remote-call))

(defn build-remote-call [kind params handler opt-args]
  {:options (ccc/->options opt-args)
   :kind    kind
   :params  params :handler handler})

(defn make-call! [{:keys [kind params] :as remote-call}]
  (log/debug "call: " kind params)
  (wsc/call! client kind params (partial handle-remote-response remote-call)))

(defn call! [state kind params handler & opt-args]
  (let [remote-call (build-remote-call kind params handler opt-args)]
    (if @(:open? state)
      (make-call! remote-call)
      (swap! (:pending-calls state) conj remote-call))))

(defn on-open [state _]
  (let [calls @(:pending-calls state)]
    (reset! (:pending-calls state) [])
    (doseq [call calls]
      (make-call! call))))

(defmulti push-handler (fn [_state message] (:kind message)))

(defmethod push-handler :ws/ping [_ _])

(defmethod push-handler :default [_ message]
  (log/warn "Unhandled push event: " message))

(defmethod push-handler :ws/hello [_ {:keys [params]}]
  (log/debug "hello: " params))

(defmethod push-handler :ws/open [state _]
  (reset! (:open? state) true)
  (when @(:reconnection? state)
    (reset! (:reconnection? state) false)
    (when-let [on-reconnected (:ws-on-reconnected @api/config)]
      (on-reconnected)))
  (let [calls @(:pending-calls state)]
    (reset! (:pending-calls state) [])
    (doseq [call calls]
      (make-call! call))))

(defmethod push-handler :ws/close [state _]
  (reset! (:open? state) false)
  (reset! (:reconnection? state) true)
  (log/warn "connection closed... reconnecting")
  (wjs/timeout 1000 (connect! @api/config)))

(defmethod push-handler :ws/error [_ _] (log/warn "websocket error"))

(defn message-handler [state message]
  (push-handler state message))

(defn- build-local-uri [path]
  (let [location (wjs/page-location)
        protocol (if (= "https:" (ccc/oget location "protocol")) "wss:" "ws:")
        host     (ccc/oget location "host")]
    (str protocol "//" host path)))

(defn- build-connection-uri [{:keys [ws-uri ws-uri-path ws-csrf-token]} connection-id]
  (let [uri (or ws-uri (build-local-uri ws-uri-path))]
    (str uri
         (if (str/includes? uri "?") "&" "?")
         "connection-id=" connection-id
         "&ws-csrf-token=" ws-csrf-token)))

(defn connect! [config]
  (let [connection-id (str (ccc/new-uuid))
        uri           (build-connection-uri config connection-id)]
    ;; `client` is (def ... nil) and reset via set! in start!; clj-kondo infers
    ;; it as nil and flags wsc/connect!'s atom arg. False positive — suppress it.
    #_{:clj-kondo/ignore [:type-mismatch]}
    (wsc/connect! client uri (:ws-csrf-token config) connection-id)))

(defn start! [state atom-fn]
  (when-not client
    (if (:ws-csrf-token @api/config)
      (do (set! client (wsc/create (partial message-handler state) :atom-fn atom-fn))
          (connect! @api/config))
      (log/error "CSRF Token missing.  Unable to start websocket."))))

(defn stop! []
  (log/info "stopping websocket"))

(defonce default-state (make-state))
(def open? (:open? default-state))
(def reconnection? (:reconnection? default-state))
(def pending-calls (:pending-calls default-state))

(defn call-default! [kind params handler & opt-args]
  (apply call! default-state kind params handler opt-args))

(defn start-default! [] (start! default-state cljs.core/atom))
