(ns c3kit.wire.mock.memory-websocket-spec
  (:require-macros [speclj.core :refer [around before context describe focus-it it redefs-around should-contain should-have-invoked should-not-have-invoked should-not-throw should-throw should= stub with with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.mock.event :as event]
            [c3kit.wire.mock.memory-websocket :as sut]
            [c3kit.wire.mock.server :as server]
            [c3kit.wire.socket :as sock]
            [c3kit.wire.spec-helper :as spec-helper]
            [clojure.string :as str]
            [speclj.stub :as stub]))

(declare sock)

(defn should-have-invoked-close-event
  ([name sock code reason] (should-have-invoked-close-event name sock code reason true))
  ([name sock code reason clean?]
   (let [[event] (stub/last-invocation-of name)]
     (should= sock (ccc/oget event "currentTarget"))
     (should= sock (ccc/oget event "srcElement"))
     (should= sock (ccc/oget event "target"))
     (should= reason (ccc/oget event "reason"))
     (should= "close" (ccc/oget event "type"))
     (should= code (ccc/oget event "code"))
     (should= 0 (ccc/oget event "eventPhase"))
     (should= 123.4567 (ccc/oget event "timeStamp"))
     (should= true (ccc/oget event "isTrusted"))
     (should= true (ccc/oget event "returnValue"))
     (should= clean? (ccc/oget event "wasClean"))
     (should= false (ccc/oget event "bubbles"))
     (should= false (ccc/oget event "cancelBubble"))
     (should= false (ccc/oget event "cancelable"))
     (should= false (ccc/oget event "composed"))
     (should= false (ccc/oget event "defaultPrevented")))))

(describe "Memory WebSocket"
  (with-stubs)
  (spec-helper/with-memory-websockets)
  (spec-helper/stub-performance-now 123.4567)

  (with sock (sut/->MemSocket "ws://example.com/foo"))
  (around [it] (log/capture-logs (it)))

  (context "constructor"

    (it "new socket"
      (should= [@sock] (server/connections))
      (should= "ws://example.com/foo" (ccc/oget @sock "url"))
      (should= "blob" (ccc/oget @sock "binaryType"))
      (should= 0 (ccc/oget @sock "bufferedAmount"))
      (should= 0 (ccc/oget @sock "readyState"))
      (should= "" (ccc/oget @sock "extensions"))
      (should= "" (ccc/oget @sock "protocol")))

    (it "new socket ending with a forward-slash"
      (let [sock (sut/->MemSocket "ws://example.com/")]
        (should= "ws://example.com/" (ccc/oget sock "url"))))

    (it "new socket without an ending forward-slash"
      (let [sock (sut/->MemSocket "ws://example.com")]
        (should= "ws://example.com/" (ccc/oget sock "url"))))

    (it "with a URI, not ending with a forward-slash"
      (let [sock (sut/->MemSocket "ws://example.com/foo")]
        (should= "ws://example.com/foo" (ccc/oget sock "url"))))

    (it "specifies a protocol"
      (let [sock (sut/->MemSocket "ws://example.com/foo" "theProtocol")]
        (should= "theProtocol" (ccc/oget sock "protocol"))))

    (it "specifies a protocol in a coll"
      (let [sock (sut/->MemSocket "ws://example.com/foo" (clj->js ["theProtocol"]))]
        (should= "theProtocol" (ccc/oget sock "protocol"))))

    (it "specifies two protocols"
      (let [sock (sut/->MemSocket "ws://example.com/foo" (clj->js ["foo" "bar"]))]
        (should= "foo" (ccc/oget sock "protocol"))))

    (it "throws when protocol is neither a string nor a collection"
      (should-throw (sut/->MemSocket "ws://example.com/foo" 1)))

    (it "throws when a protocol is specified more than once"
      (should-throw js/SyntaxError "Protocols may not contain duplicates: [\"foo\" \"bar\" \"foo\"]"
        (sut/->MemSocket "ws://example.com/foo" (clj->js ["foo" "bar" "foo"]))))

    (it "throws when protocol is not ws:// or wss://"
      (should-throw js/SyntaxError "URL is invalid: http://example.com/foo"
        (sut/->MemSocket "http://example.com/foo"))
      (should-throw js/SyntaxError (sut/->MemSocket "https://example.com/foo"))
      (should-throw js/SyntaxError (sut/->MemSocket "ws:/example.com/foo"))
      (should-throw js/SyntaxError (sut/->MemSocket "wss:/example.com/foo"))
      (should-throw js/SyntaxError (sut/->MemSocket "example.com/foo"))
      (should-not-throw (sut/->MemSocket "ws://example.com/foo"))
      (should-not-throw (sut/->MemSocket "wss://example.com/foo")))

    (it "throws when a fragment exists in the url"
      (should-throw js/SyntaxError "URL is invalid: ws://example.com/foo#"
        (sut/->MemSocket "ws://example.com/foo#"))
      (should-throw js/SyntaxError (sut/->MemSocket "ws://example.com/foo#blah"))
      (should-throw js/SyntaxError (sut/->MemSocket "ws://example.c#om/middle")))
    )

  (context "send"

    (it "throws when in CONNECTING state"
      (should-throw js/Error "MemSocket is still in CONNECTING state." (sock/send! @sock "data")))

    (it "does nothing when in CONNECTED state"
      (ccc/oset @sock "readyState" 1)
      (sock/send! @sock "data")
      (should= "" (log/captured-logs-str)))

    (it "logs error when in CLOSING state"
      (ccc/oset @sock "readyState" 2)
      (sock/send! @sock "data")
      (should= "MemSocket is already in CLOSING or CLOSED state." (log/captured-logs-str)))

    (it "logs error when in CLOSED state"
      (ccc/oset @sock "readyState" 3)
      (sock/send! @sock "data")
      (should= "MemSocket is already in CLOSING or CLOSED state." (log/captured-logs-str)))

    (it "sends a message to the server"
      (ccc/oset @sock "readyState" 1)
      (sock/send! @sock "data")
      (should= ["data"] (server/messages @sock)))

    )

  (context "on open"

    (it "no handler set"
      (should-not-throw (wjs/dispatch-event @sock (event/->OpenEvent @sock))))

    (it "invokes sole event handler"
      (wjs/add-listener @sock "open" (stub :on-open))
      (let [event (event/->OpenEvent @sock)]
        (wjs/dispatch-event @sock event)
        (should-have-invoked :on-open {:with [event]})))

    (it "invokes two event handlers"
      (wjs/add-listener @sock "open" (stub :on-open-1))
      (wjs/add-listener @sock "open" (stub :on-open-2))
      (let [event (event/->OpenEvent @sock)]
        (wjs/dispatch-event @sock event)
        (should-have-invoked :on-open-1 {:with [event]})
        (should-have-invoked :on-open-2 {:with [event]})))

    (it "exception in the middle of the callback loop"
      (wjs/add-listener @sock "open" (stub :on-open-1))
      (wjs/add-listener @sock "open" (stub :on-open-2 {:throw "open error"}))
      (wjs/add-listener @sock "open" (stub :on-open-3))
      (let [event (event/->OpenEvent @sock)]
        (wjs/dispatch-event @sock event)
        (should-have-invoked :on-open-1 {:with [event]})
        (should-have-invoked :on-open-2 {:with [event]})
        (should-have-invoked :on-open-3 {:with [event]})
        (should-contain "Error occurred in MemSocket open open error" (log/captured-logs-str))))
    )

  (context "on message"
    (it "no handler set"
      (should-not-throw (wjs/dispatch-event @sock (event/->MessageEvent @sock "blah"))))

    (it "invokes sole event handler"
      (wjs/add-listener @sock "message" (stub :on-message))
      (let [event (event/->MessageEvent @sock "blah")]
        (wjs/dispatch-event @sock event)
        (should-have-invoked :on-message {:with [event]})))

    (it "invokes two event handlers"
      (wjs/add-listener @sock "message" (stub :on-message-1))
      (wjs/add-listener @sock "message" (stub :on-message-2))
      (let [event (event/->MessageEvent @sock "blah")]
        (wjs/dispatch-event @sock event)
        (should-have-invoked :on-message-1 {:with [event]})
        (should-have-invoked :on-message-2 {:with [event]})))

    (it "exception in the middle of the callback loop"
      (wjs/add-listener @sock "message" (stub :on-message-1))
      (wjs/add-listener @sock "message" (stub :on-message-2 {:throw "message error"}))
      (wjs/add-listener @sock "message" (stub :on-message-3))
      (let [event (event/->MessageEvent @sock "blah")]
        (wjs/dispatch-event @sock event)
        (should-have-invoked :on-message-1 {:with [event]})
        (should-have-invoked :on-message-2 {:with [event]})
        (should-have-invoked :on-message-3 {:with [event]})
        (should-contain "Error occurred in MemSocket message message error" (log/captured-logs-str))))

    (it "removes event listener"
      (let [listener-1 (stub :listener-1)
            listener-2 (stub :listener-2)
            listener-3 (stub :listener-3)]
        (wjs/add-listener @sock "message" listener-1)
        (wjs/add-listener @sock "message" listener-2)
        (wjs/add-listener @sock "message" listener-3)
        (wjs/remove-listener @sock "message" listener-2)
        (let [event (event/->MessageEvent @sock "blah")]
          (wjs/dispatch-event @sock event)
          (should-have-invoked :listener-1 {:with [event]})
          (should-not-have-invoked :listener-2)
          (should-have-invoked :listener-3 {:with [event]}))))

    (it "maintains execution order after removal of event"
      (let [state      (atom nil)
            listener-1 #(reset! state 1)
            listener-2 #(reset! state 2)
            listener-3 #(reset! state 3)
            listener-4 #(reset! state 4)]
        (wjs/add-listener @sock "message" listener-1)
        (wjs/add-listener @sock "message" listener-2)
        (wjs/add-listener @sock "message" listener-3)
        (wjs/remove-listener @sock "message" listener-2)
        (wjs/add-listener @sock "message" listener-4)
        (let [event (event/->MessageEvent @sock "blah")]
          (wjs/dispatch-event @sock event)
          (should= 4 @state))))

    (it "does not add an event multiple times"
      (let [state      (atom nil)
            listener-1 (stub :listener-1 {:invoke #(reset! state 1)})
            listener-2 (stub :listener-2 {:invoke #(reset! state 2)})
            listener-3 (stub :listener-3 {:invoke #(reset! state 3)})]
        (wjs/add-listener @sock "message" listener-1)
        (wjs/add-listener @sock "message" listener-2)
        (wjs/add-listener @sock "message" listener-3)
        (wjs/add-listener @sock "message" listener-1)
        (let [event (event/->MessageEvent @sock "blah")]
          (wjs/dispatch-event @sock event)
          (should-have-invoked :listener-1 {:times 1})
          (should= 3 @state))))

    )

  (context "on error"
    (it "no handler set"
      (should-not-throw (wjs/dispatch-event @sock (event/->ErrorEvent @sock))))

    (it "invokes sole event handler"
      (wjs/add-listener @sock "error" (stub :on-error))
      (let [event (event/->ErrorEvent @sock)]
        (wjs/dispatch-event @sock event)
        (should-have-invoked :on-error {:with [event]})))

    (it "invokes two event handlers"
      (wjs/add-listener @sock "error" (stub :on-error-1))
      (wjs/add-listener @sock "error" (stub :on-error-2))
      (let [event (event/->ErrorEvent @sock)]
        (wjs/dispatch-event @sock event)
        (should-have-invoked :on-error-1 {:with [event]})
        (should-have-invoked :on-error-2 {:with [event]})))

    (it "exception in the middle of the callback loop"
      (wjs/add-listener @sock "error" (stub :on-error-1))
      (wjs/add-listener @sock "error" (stub :on-error-2 {:throw "error handler error"}))
      (wjs/add-listener @sock "error" (stub :on-error-3))
      (let [event (event/->ErrorEvent @sock)]
        (wjs/dispatch-event @sock event)
        (should-have-invoked :on-error-1 {:with [event]})
        (should-have-invoked :on-error-2 {:with [event]})
        (should-have-invoked :on-error-3 {:with [event]})
        (should-contain "Error occurred in MemSocket error error handler error" (log/captured-logs-str))))

    (it "removes event listener"
      (let [listener-1 (stub :listener-1)
            listener-2 (stub :listener-2)
            listener-3 (stub :listener-3)]
        (wjs/add-listener @sock "error" listener-1)
        (wjs/add-listener @sock "error" listener-2)
        (wjs/add-listener @sock "error" listener-3)
        (wjs/remove-listener @sock "error" listener-2)
        (let [event (event/->ErrorEvent @sock)]
          (wjs/dispatch-event @sock event)
          (should-have-invoked :listener-1 {:with [event]})
          (should-not-have-invoked :listener-2)
          (should-have-invoked :listener-3 {:with [event]}))))
    )

  (context "close"
    (before (ccc/oset @sock "readyState" 1))

    (it "updates readyState to 3"
      (sock/close! @sock)
      (should= 3 (ccc/oget @sock "readyState")))

    (it "defaults closing code to 1000"
      (sock/close! @sock)
      (should= "Closing MemSocket with code: 1000" (log/captured-logs-str)))

    (it "code is neither 1000 nor an integer between 3000 and 4999"
      (should-throw (sock/close! @sock 2999))
      (should-throw (sock/close! @sock 5000))
      (should-throw (sock/close! @sock 2999 "reason"))
      (should-throw (sock/close! @sock 5000 "reason"))
      (should-not-throw (sock/close! @sock 1000))
      (should-not-throw (sock/close! @sock 3000))
      (should-not-throw (sock/close! @sock 4000))
      (should-not-throw (sock/close! @sock 4999))
      (should-not-throw (sock/close! @sock 1000 "reason"))
      (should-not-throw (sock/close! @sock 3000 "reason"))
      (should-not-throw (sock/close! @sock 4000 "reason"))
      (should-not-throw (sock/close! @sock 4999 "reason")))

    (it "closes with specified code"
      (sock/close! @sock 3000)
      (should= 3 (ccc/oget @sock "readyState"))
      (should= "Closing MemSocket with code: 3000" (log/captured-logs-str)))

    (it "closes with reason"
      (sock/close! @sock 3000 "just because")
      (should= 3 (ccc/oget @sock "readyState"))
      (let [[log-1 log-2] (str/split (log/captured-logs-str) "\n")]
        (should= "Closing MemSocket with code: 3000" log-1)
        (should= "Closing MemSocket with reason: just because" log-2)))

    (it "closes with reason of exactly 123 bytes"
      (let [reason (apply str (repeat 123 "a"))]
        (sock/close! @sock 1000 reason)
        (should= 3 (ccc/oget @sock "readyState"))
        (let [[log-1 log-2] (str/split (log/captured-logs-str) "\n")]
          (should= "Closing MemSocket with code: 1000" log-1)
          (should= (str "Closing MemSocket with reason: " reason) log-2))))

    (it "throws if reason code is greater than 123 bytes and does not close"
      (should-throw (sock/close! @sock 1000 (apply str (repeat 124 "a"))))
      (should= 1 (ccc/oget @sock "readyState"))
      (should= "" (log/captured-logs-str)))

    (it "counts reason size via UTF-8 encoding"
      (let [emoji-char "🥸" ; 4 bytes
            reason     (apply str (repeat 31 emoji-char))]
        (should-throw (sock/close! @sock 1000 reason))
        (should= 1 (ccc/oget @sock "readyState"))
        (should= "" (log/captured-logs-str))))

    (context "on close handler"
      (it "unassigned"
        (should-not-throw (wjs/dispatch-event @sock (event/->CloseEvent @sock 1000 "" true))))

      (it "already closed"
        (sock/close! @sock)
        (should-not-throw (sock/close! @sock)))

      (it "on-close fires when close is invoked"
        (wjs/add-listener @sock "close" (stub :on-close))
        (sock/close! @sock)
        (should-have-invoked-close-event :on-close @sock 1000 ""))

      (it "on-close fires when close is invoked with a code and reason"
        (wjs/add-listener @sock "close" (stub :on-close))
        (sock/close! @sock 3000 "the reason")
        (should-have-invoked-close-event :on-close @sock 3000 "the reason"))

      (it "onclose does not fire when reason is too long"
        (let [reason (apply str (repeat 124 "a"))]
          (wjs/add-listener @sock "close" (stub :on-close))
          (should-throw (sock/close! @sock 1000 reason))
          (should-not-have-invoked :on-close)))

      (it "onclose fires many event handlers"
        (wjs/add-listener @sock "close" (stub :handler-1))
        (wjs/add-listener @sock "close" (stub :handler-2))
        (sock/close! @sock 3000 "another reason")
        (should-have-invoked-close-event :handler-1 @sock 3000 "another reason")
        (should-have-invoked-close-event :handler-2 @sock 3000 "another reason"))

      (it "close handler throws, but does not stop remaining handlers"
        (wjs/add-listener @sock "close" (stub :handler-1))
        (wjs/add-listener @sock "close" (stub :handler-2 {:throw "the error"}))
        (wjs/add-listener @sock "close" (stub :handler-3))
        (sock/close! @sock 3000 "another reason")
        (should-have-invoked-close-event :handler-1 @sock 3000 "another reason" false)
        (should-have-invoked-close-event :handler-2 @sock 3000 "another reason" false)
        (should-have-invoked-close-event :handler-3 @sock 3000 "another reason" false)
        (should-contain "Error occurred in MemSocket close the error" (log/captured-logs-str)))
      )
    )
  )
