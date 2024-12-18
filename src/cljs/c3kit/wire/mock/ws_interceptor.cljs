(ns c3kit.wire.mock.ws-interceptor
  (:require [c3kit.wire.mock.server :as server]))

(def JsWebSocket js/WebSocket)
(defn- ->initiate [ws]
  (server/initiate ws)
  ws)

(defn ->WebSocketInterceptor
  ([url] (->initiate (JsWebSocket. url)))
  ([url protocols] (->initiate (JsWebSocket. url protocols))))
