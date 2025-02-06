(ns c3kit.wire.mock.event
  (:require [c3kit.apron.corec :as ccc]))

(def JsWebSocket js/WebSocket)

;region API

;; TODO [BAC]: Could be better (maybe?)... will work for now
(defmulti ->OpenEvent (fn [ws] (type ws)))
(defmulti ->CloseEvent (fn [ws _code _reason _clean?] (type ws)))
(defmulti ->MessageEvent (fn [ws _data] (type ws)))
(defmulti ->ErrorEvent (fn [ws] (type ws)))

;endregion

(defn- with-message-props [e sock data]
  (doto e
    (ccc/oset "lastEventId" "")
    (ccc/oset "data" data)
    (ccc/oset "ports" [])
    (ccc/oset "origin" (re-find #"^wss?://[^\/]*" (ccc/oget sock "url")))))

(defn- with-close-props [e code reason clean?]
  (doto e
    (ccc/oset "code" code)
    (ccc/oset "reason" reason)
    (ccc/oset "wasClean" clean?)))

;region Native Events

(defmethod ->MessageEvent JsWebSocket [ws data]
  (-> (js/Event. "message")
      (with-message-props ws data)))

(defmethod ->OpenEvent JsWebSocket [_ws] (js/Event. "open"))
(defmethod ->ErrorEvent JsWebSocket [_ws] (js/Event. "error"))
(defmethod ->CloseEvent JsWebSocket [_ws code reason clean?]
  (-> (js/Event. "close")
      (with-close-props code reason clean?)))

;endregion

;region Memory Events

(defn- ->MemEvent [element]
  (js-obj
    "isTrusted" true
    "bubbles" false
    "cancelBubble" false
    "cancelable" false
    "composed" false
    "currentTarget" element
    "defaultPrevented" false
    "eventPhase" 0
    "returnValue" true
    "srcElement" element
    "target" element
    "timeStamp" (js-invoke js/performance "now")))

(defmethod ->OpenEvent :default [sock]
  (doto (->MemEvent sock)
    (ccc/oset "type" "open")))

(defmethod ->CloseEvent :default [sock code reason clean?]
  (doto (->MemEvent sock)
    (ccc/oset "type" "close")
    (with-close-props code reason clean?)))

(defmethod ->MessageEvent :default [sock data]
  (doto (->MemEvent sock)
    (ccc/oset "type" "message")
    (with-message-props sock data)))

(defmethod ->ErrorEvent :default [sock]
  (doto (->MemEvent sock)
    (ccc/oset "type" "error")))

;endregion
