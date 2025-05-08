(ns c3kit.wire.websocket
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.api :as api]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.websocketc :as wsc]
            [clojure.string :as str]
            [reagent.core :as reagent]))

(def client nil)
(def open? (reagent/atom false))
(def reconnection? (atom false))
(defonce pending-calls (atom []))
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

(defn call! [kind params handler & opt-args]
  (let [remote-call (build-remote-call kind params handler opt-args)]
    (if @open?
      (make-call! remote-call)
      (swap! pending-calls conj remote-call))))

(defn on-open [_]
  (let [calls @pending-calls]
    (reset! pending-calls [])
    (doseq [call calls]
      (make-call! call))))

(defmulti push-handler :kind)

(defmethod push-handler :ws/ping [_]) ; nothing to do really

(defmethod push-handler :default [message]
  (log/warn "Unhandled push event: " message))

(defmethod push-handler :ws/hello [{:keys [params]}]
  (log/debug "hello: " params))

(defmethod push-handler :ws/open [_]
  (reset! open? true)
  (when @reconnection?
    (reset! reconnection? false)
    (when-let [on-reconnected (:ws-on-reconnected @api/config)]
      (on-reconnected)))
  (let [calls @pending-calls]
    (reset! pending-calls [])
    (doseq [call calls]
      (make-call! call))))

(defmethod push-handler :ws/close [_]
  (reset! open? false)
  (reset! reconnection? true)
  (log/warn "connection closed... reconnecting")
  (wjs/timeout 1000 (connect! @api/config)))

(defmethod push-handler :ws/error [_] (log/warn "websocket error"))

; ----------------- end handlers ------------------------

(defn message-handler [message]
  ;(log/info "received message: " message)
  (push-handler message))

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
    (wsc/connect! client uri (:ws-csrf-token config) connection-id)))

(defn start! []
  (when-not client
    (if (:ws-csrf-token @api/config)
      (do (set! client (wsc/create message-handler :atom-fn reagent/atom))
          (connect! @api/config))
      (log/error "CSRF Token missing.  Unable to start websocket."))))

(defn stop! []
  ;; TODO - MDM: add close capability
  (log/info "stopping websocket"))

(defn disconnected-button []
  (let [open? (reagent/atom false)]
    (fn []
      [:div.contextual-menu-anchor
       [:button#-disconnected-button.disconnected.naked {:on-click #(reset! open? true)}
        [:span.fas.fa-exclamation-triangle.animation.error.small-margin-left]]
       (when @open?
         [:div#-disconnected-menu-overlay.contextual-menu {:on-click #(reset! open? false)}
          [:div#-disconnected-menu.card
           [:h5.small-margin-bottom [:span.fas.fa-link] "Connection Broken"]
           ;[:p (pr-str @ws/channel-state)]
           [:p.margin-bottom "Your connection with the server has been broken. "
            "We are trying to reconnect.  If that doesn't seem to help, please try reloading this page."]
           [:button.primary {:on-click wjs/page-reload!} "Reload Page"]]])])))

(defn connection-status [] (when-not @open? [disconnected-button]))
