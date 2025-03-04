(ns c3kit.wire.mock.memory-server-spec
  (:require-macros [speclj.core :refer [around context describe it should should-be-nil should-contain should-have-invoked should-not-have-invoked should-throw should= stub with with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.mock.server :as server]
            [c3kit.wire.socket :as sock]
            [c3kit.wire.spec-helper :as spec-helper]
            [speclj.core]
            [speclj.stub :as stub]))

(declare sock)

(defn ->add-event-listener [sock]
  (fn [event handler]
    (let [event    (str "on" event)
          existing (ccc/oget sock event)]
      (ccc/oset sock event (juxt existing handler)))))

(defn ->Socket [ready-state]
  (let [sock (js-obj "readyState" ready-state)]
    (ccc/oset sock "addEventListener" (->add-event-listener sock))
    sock))

(describe "Memory Server"
  (with-stubs)
  (spec-helper/with-memory-websockets)
  (spec-helper/stub-performance-now 123.4567)

  (around [it] (log/capture-logs (it)))

  (with sock (js/WebSocket. "ws://example.com"))

  (it "connections"
    (should= [@sock] (server/connections))
    (let [sock-2 (js/WebSocket. "ws://blah.com")]
      (should= [@sock sock-2] (server/connections))
      (sock/close! @sock)
      (should= [sock-2] (server/connections))
      (sock/close! sock-2)
      (should= [] (server/connections))))

  (it "receives arbitrary data for a socket and flushes it out"
    (server/receive @sock "blah")
    (should= ["blah"] (server/messages @sock))
    (server/receive @sock "foo")
    (should= ["blah" "foo"] (server/messages @sock))
    (server/flush @sock)
    (should-be-nil (server/messages @sock)))

  (context "initiate"

    (it "throws when socket is already open"
      (should-throw js/Error "Socket is not CONNECTING"
        (server/initiate (->Socket 1))))

    (it "throws when socket is closing"
      (should-throw js/Error "Socket is not CONNECTING"
        (server/initiate (->Socket 2))))

    (it "throws when socket is closing"
      (should-throw js/Error "Socket is not CONNECTING"
        (server/initiate (->Socket 2))))

    (it "stores socket in-memory"
      (let [socket (->Socket 0)]
        (server/initiate socket)
        (should-contain socket @(:sockets @server/impl))))

    (it "throws when socket has already been initiated"
      (let [socket (->Socket 0)]
        (server/initiate socket)
        (should-throw js/Error "Socket has already been initiated" (server/initiate socket))))
    )

  (context "open"

    (it "throws when already open"
      (ccc/oset @sock "readyState" 1)
      (should-throw js/Error "Socket is not CONNECTING" (server/open @sock)))

    (it "throws when closing"
      (ccc/oset @sock "readyState" 2)
      (should-throw js/Error "Socket is not CONNECTING" (server/open @sock)))

    (it "throws when already closed"
      (ccc/oset @sock "readyState" 3)
      (should-throw js/Error "Socket is not CONNECTING" (server/open @sock)))

    (it "opens a websocket"
      (wjs/add-listener @sock "open" (comp (stub :open) js->clj))
      (should (sock/connecting? @sock))
      (server/open @sock)
      (should (sock/open? @sock))
      (let [[event] (stub/last-invocation-of :open)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "open" (get event "type"))))

    (it "references socket object in open event"
      (wjs/add-listener @sock "open" (stub :open))
      (server/open @sock)
      (let [[event] (stub/last-invocation-of :open)]
        (should= @sock (ccc/oget event "currentTarget"))
        (should= @sock (ccc/oget event "srcElement"))
        (should= @sock (ccc/oget event "target"))))

    )

  (context "close"

    (it "throws if client is already closed"
      (ccc/oset @sock "readyState" 3)
      (should-throw js/Error "Socket is not CONNECTING or OPEN" (server/close @sock)))

    (it "throws if client is in the process of closing"
      (ccc/oset @sock "readyState" 2)
      (should-throw js/Error "Socket is not CONNECTING or OPEN" (server/close @sock)))

    (it "closes and invokes onclose when client is connecting"
      (ccc/oset @sock "readyState" 0)
      (wjs/add-listener @sock "close" (stub :close))
      (server/close @sock)
      (should (sock/closed? @sock))
      (should-have-invoked :close))

    (it "closes and invokes onclose when client is connected"
      (ccc/oset @sock "readyState" 1)
      (wjs/add-listener @sock "close" (comp (stub :close) js->clj))
      (server/close @sock)
      (let [[event] (stub/last-invocation-of :close)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= 1000 (get event "code"))
        (should= "closed by server" (get event "reason"))
        (should= true (get event "wasClean"))
        (should= "close" (get event "type"))))

    (it "close event references socket object"
      (ccc/oset @sock "readyState" 1)
      (wjs/add-listener @sock "close" (stub :close))
      (server/close @sock)
      (let [[event] (stub/last-invocation-of :close)]
        (should= @sock (ccc/oget event "currentTarget"))
        (should= @sock (ccc/oget event "srcElement"))
        (should= @sock (ccc/oget event "target"))))
    )

  (context "send"

    (it "throws when client is CONNECTING"
      (should-throw js/Error "Socket is not OPEN" (server/send @sock "the message")))

    (it "throws when client is CLOSING"
      (ccc/oset @sock "readyState" 2)
      (should-throw js/Error "Socket is not OPEN" (server/send @sock "the message")))

    (it "throws when client is CLOSED"
      (ccc/oset @sock "readyState" 3)
      (should-throw js/Error "Socket is not OPEN" (server/send @sock "the message")))

    (it "invokes message handler"
      (ccc/oset @sock "readyState" 1)
      (wjs/add-listener @sock "message" (comp (stub :message) js->clj))
      (server/send @sock "the message")
      (let [[event] (stub/last-invocation-of :message)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= "the message" (get event "data"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= "" (get event "lastEventId"))
        (should= "ws://example.com" (get event "origin"))
        (should= [] (get event "ports"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "message" (get event "type"))))

    (it "message handler event references websocket object"
      (ccc/oset @sock "readyState" 1)
      (wjs/add-listener @sock "message" (stub :message))
      (server/send @sock "the message")
      (let [[event] (stub/last-invocation-of :message)]
        (should= @sock (ccc/oget event "currentTarget"))
        (should= @sock (ccc/oget event "srcElement"))
        (should= @sock (ccc/oget event "target"))))

    (it "origin excludes the websocket URL's path"
      (ccc/oset @sock "readyState" 1)
      (ccc/oset @sock "url" "ws://localhost:1234/foo")
      (wjs/add-listener @sock "message" (stub :message))
      (server/send @sock "the message")
      (let [[event] (stub/last-invocation-of :message)]
        (should= "ws://localhost:1234" (ccc/oget event "origin"))))

    )

  (context "reject"

    (it "throws when socket is already open"
      (ccc/oset @sock "readyState" 1)
      (should-throw js/Error "Socket is not CONNECTING" (server/reject @sock 4000 "nope")))

    (it "throws when socket is closing"
      (ccc/oset @sock "readyState" 2)
      (should-throw js/Error "Socket is not CONNECTING" (server/reject @sock 4000 "nope")))

    (it "throws when socket is closed"
      (ccc/oset @sock "readyState" 3)
      (should-throw js/Error "Socket is not CONNECTING" (server/reject @sock 4000 "nope")))

    (it "invokes onerror and onclose"
      (wjs/add-listener @sock "close" (stub :close))
      (wjs/add-listener @sock "error" (stub :error))
      (server/reject @sock 4000 "nope")
      (should (sock/closed? @sock))
      (should-have-invoked :error)
      (should-have-invoked :close))

    (it "onerror event data"
      (wjs/add-listener @sock "error" (comp (stub :error) js->clj))
      (server/reject @sock 4000 "nope")
      (let [[event] (stub/last-invocation-of :error)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= 0 (get event "eventPhase"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "error" (get event "type"))))

    (it "onclose event data"
      (wjs/add-listener @sock "close" (comp (stub :close) js->clj))
      (server/reject @sock 4000 "nope")
      (let [[event] (stub/last-invocation-of :close)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= 4000 (get event "code"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= 0 (get event "eventPhase"))
        (should= "nope" (get event "reason"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "close" (get event "type"))
        (should= false (get event "wasClean"))))

    (it "onclose event references socket object"
      (wjs/add-listener @sock "close" (stub :close))
      (server/reject @sock 4001 "blah")
      (let [[event] (stub/last-invocation-of :close)]
        (should= 4001 (ccc/oget event "code"))
        (should= @sock (ccc/oget event "currentTarget"))
        (should= "blah" (ccc/oget event "reason"))
        (should= @sock (ccc/oget event "srcElement"))
        (should= @sock (ccc/oget event "target"))))
    )

  (context "shutdown"

    (it "throws when performing any operation after being shutdown"
      (server/shutdown)
      (should-throw js/Error "Server is not running" (server/shutdown))
      (should-throw js/Error "Server is not running" (server/open @sock))
      (should-throw js/Error "Server is not running" (server/reject @sock 4000 "blah"))
      (should-throw js/Error "Server is not running" (server/close @sock))
      (should-throw js/Error "Server is not running" (server/send @sock "foo")))

    (it "invokes onerror for all active sockets"
      (let [sock-2 (js/WebSocket. "ws://example.com")]
        (wjs/add-listener @sock "error" (stub :error-1))
        (wjs/add-listener sock-2 "error" (stub :error-2))
        (server/shutdown)
        (should (sock/closed? @sock))
        (should (sock/closed? sock-2))
        (should-have-invoked :error-1)
        (should-have-invoked :error-2)))

    (it "onerror event data"
      (wjs/add-listener @sock "error" (comp (stub :error) js->clj))
      (server/shutdown)
      (let [[event] (stub/last-invocation-of :error)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "error" (get event "type"))))

    (it "onerror event references socket object"
      (wjs/add-listener @sock "error" (stub :error))
      (server/shutdown)
      (let [[event] (stub/last-invocation-of :error)]
        (should= @sock (ccc/oget event "currentTarget"))
        (should= @sock (ccc/oget event "srcElement"))
        (should= @sock (ccc/oget event "target"))))

    (it "invokes onclose for all active sockets"
      (let [sock-2 (js/WebSocket. "ws://example.com")]
        (wjs/add-listener @sock "close" (stub :close-1))
        (wjs/add-listener sock-2 "close" (stub :close-2))
        (server/shutdown)
        (should-have-invoked :close-1)
        (should-have-invoked :close-2)))

    (it "onclose event data"
      (wjs/add-listener @sock "close" (comp (stub :close) js->clj))
      (server/shutdown)
      (let [[event] (stub/last-invocation-of :close)
            clj-sock (js->clj @sock)]
        (should= true (get event "isTrusted"))
        (should= false (get event "bubbles"))
        (should= false (get event "cancelBubble"))
        (should= false (get event "cancelable"))
        (should= 1006 (get event "code"))
        (should= false (get event "composed"))
        (should= clj-sock (get event "currentTarget"))
        (should= false (get event "defaultPrevented"))
        (should= 0 (get event "eventPhase"))
        (should= "" (get event "reason"))
        (should= true (get event "returnValue"))
        (should= clj-sock (get event "srcElement"))
        (should= clj-sock (get event "target"))
        (should= 123.4567 (get event "timeStamp"))
        (should= "close" (get event "type"))
        (should= false (get event "wasClean"))))

    (it "onclose event references socket object"
      (wjs/add-listener @sock "close" (stub :close))
      (server/shutdown)
      (let [[event] (stub/last-invocation-of :close)]
        (should= @sock (ccc/oget event "currentTarget"))
        (should= @sock (ccc/oget event "srcElement"))
        (should= @sock (ccc/oget event "target"))))

    (it "onerror is invoked before onclose"
      (let [atm (atom nil)]
        (wjs/add-listener @sock "error" #(reset! atm :error))
        (wjs/add-listener @sock "close" #(reset! atm :close))
        (server/shutdown)
        (should= :close @atm)))

    (it "does nothing with closing sockets"
      (wjs/add-listener @sock "error" (stub :error))
      (wjs/add-listener @sock "close" (stub :close))
      (ccc/oset @sock "readyState" 2)
      (server/shutdown)
      (should-not-have-invoked :close)
      (should-not-have-invoked :error)
      (should (sock/closing? @sock)))

    (it "does nothing with closed sockets"
      (wjs/add-listener @sock "error" (stub :error))
      (wjs/add-listener @sock "close" (stub :close))
      (ccc/oset @sock "readyState" 3)
      (server/shutdown)
      (should-not-have-invoked :close)
      (should-not-have-invoked :error)
      (should (sock/closed? @sock)))
    )
  )
