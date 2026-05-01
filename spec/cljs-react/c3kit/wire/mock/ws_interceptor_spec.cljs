(ns c3kit.wire.mock.ws-interceptor-spec
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.mock.event :as event]
            [c3kit.wire.mock.memory-server :as mem-server]
            [c3kit.wire.mock.server :as server]
            [c3kit.wire.mock.ws-interceptor :as sut]
            [c3kit.wire.socket :as sock]
            [c3kit.wire.spec-helper :as spec-helper]
            [speclj.core :refer-macros [after around before context describe it should-be-a should-have-invoked should= stub with with-stubs]]))

(declare sock)

(describe "WebSocket Interceptor"
  (with-stubs)
  (before (reset! server/impl (mem-server/->MemServer)))
  (after (run! sock/close! (server/connections)))

  (context "->WebSocketInterceptor"

    (it "url only"
      (let [ws (sut/->WebSocketInterceptor "ws://localhost:8080")]
        (should-be-a js/WebSocket ws)
        (should= "ws://localhost:8080/" (ccc/oget ws "url"))
        (should= "" (ccc/oget ws "protocol"))
        (should= [ws] (server/connections))))

    (it "with protocols"
      (let [ws (sut/->WebSocketInterceptor "ws://localhost:8080" "foo")]
        (should-be-a js/WebSocket ws)
        (should= "ws://localhost:8080/" (ccc/oget ws "url"))
        (should= "" (ccc/oget ws "protocol"))
        (should= [ws] (server/connections))))

    (it "with WebSocket alias"
      (let [js-web-socket js/WebSocket]
        (set! js/WebSocket sut/->WebSocketInterceptor)
        (let [ws (js/WebSocket. "ws://localhost:8080")]
          (should-be-a js-web-socket ws)
          (should= [ws] (server/connections)))))

    (context "events"
      (spec-helper/with-websocket-impl sut/->WebSocketInterceptor)

      (with sock (js/WebSocket. "ws://localhost:8080"))

      (it "message"
        (let [event (event/->MessageEvent @sock "blah")]
          (wjs/add-listener @sock "message" (stub :listener))
          (wjs/dispatch-event @sock event)
          (should-have-invoked :listener {:with [event]})
          (should= "message" (ccc/oget event "type"))
          (should= "blah" (ccc/oget event "data"))
          (should= "ws://localhost:8080" (ccc/oget event "origin"))
          (should= "" (ccc/oget event "lastEventId"))
          ;; TODO [BAC]: ports should= ?
          (should= [] (ccc/oget event "ports"))))

      (it "close"
        (let [event (event/->CloseEvent @sock 1006 "just because" true)]
          (wjs/add-listener @sock "close" (stub :listener))
          (wjs/dispatch-event @sock event)
          (should-have-invoked :listener {:with [event]})
          (should= "close" (ccc/oget event "type"))
          (should= 1006 (ccc/oget event "code"))
          (should= "just because" (ccc/oget event "reason"))
          (should= true (ccc/oget event "wasClean"))))

      (it "open"
        (let [event (event/->OpenEvent @sock)]
          (wjs/add-listener @sock "open" (stub :listener))
          (wjs/dispatch-event @sock event)
          (should-have-invoked :listener {:with [event]})
          (should= "open" (ccc/oget event "type"))))

      (it "error"
        (let [event       (event/->ErrorEvent @sock)
              error-event (atom nil)
              listener    #(reset! error-event %)]
          (wjs/add-listener @sock "error" listener)
          (wjs/dispatch-event @sock event)
          (should= event @error-event)
          (should= "error" (ccc/oget event "type"))))
      )
    )
  )
