(ns c3kit.wire.websocket-spec
  (:require-macros [speclj.core :refer [after around context describe it redefs-around should-have-invoked should-not-have-invoked
                                        should-not= should= stub with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.api :as api]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.websocket :as sut]
            [c3kit.wire.websocketc :as wsc]
            [speclj.core]))

(def dummy-uuid (ccc/new-uuid))

(describe "Websocket"
  (with-stubs)
  (around [it] (log/capture-logs (it)))

  (redefs-around [sut/make-call! (stub :make-call!)])

  (it "on-connect callback"
    (sut/call! :some/call {} ccc/noop)
    (should-not-have-invoked :make-call!)
    (sut/message-handler {:kind :ws/open})
    (should-have-invoked :make-call!))

  (it "on-connect invokes immediately if already connected"
    (set! sut/client (atom {:connection {:open? true}}))
    (sut/call! :some/call {} ccc/noop)
    (should-have-invoked :make-call!))

  (context "connecting"

    (around [it]
      (with-redefs [wsc/connect! (stub :wsc/connect!)
                    ccc/new-uuid (constantly dummy-uuid)]
        (api/configure! :ws-on-reconnected (stub :reconnected!))
        (it)))

    (it "uses ws-uri"
      (sut/connect! {:ws-uri        "the-ws-uri"
                     :ws-csrf-token "the-csrf-token"})
      (let [uri (str "the-ws-uri?connection-id=" dummy-uuid "&ws-csrf-token=the-csrf-token")]
        (should-have-invoked :wsc/connect! {:with [:* uri "the-csrf-token" (str dummy-uuid)]})))

    (it "uses ws-uri with query parameters"
      (sut/connect! {:ws-uri        "the-ws-uri?foo=bar"
                     :ws-csrf-token "the-csrf-token"})
      (let [uri (str "the-ws-uri?foo=bar&connection-id=" dummy-uuid "&ws-csrf-token=the-csrf-token")]
        (should-have-invoked :wsc/connect! {:with [:* uri "the-csrf-token" (str dummy-uuid)]})))

    (it "uses ws-uri-path with an http host"
      (let [js-location (js-obj
                          "host" "the-host"
                          "protocol" "http:")
            uri         (str "ws://the-host/the-ws-path"
                             "?connection-id=" dummy-uuid
                             "&ws-csrf-token=the-csrf-token")]
        (with-redefs [wjs/page-location (constantly js-location)]
          (sut/connect! {:ws-uri-path   "/the-ws-path"
                         :ws-csrf-token "the-csrf-token"})
          (should-have-invoked :wsc/connect! {:with [:* uri "the-csrf-token" (str dummy-uuid)]}))))

    (it "uses ws-uri-path with a https host"
      (let [js-location (js-obj
                          "host" "the-host"
                          "protocol" "https:")
            uri         (str "wss://the-host/the-ws-path"
                             "?connection-id=" dummy-uuid
                             "&ws-csrf-token=the-csrf-token")]
        (with-redefs [wjs/page-location (constantly js-location)]
          (sut/connect! {:ws-uri-path   "/the-ws-path"
                         :ws-csrf-token "the-csrf-token"})
          (should-have-invoked :wsc/connect! {:with [:* uri "the-csrf-token" (str dummy-uuid)]}))))

    #_(context "connection uri"

        (it "not secure"
          (let [location (clj->js {:host "site.com" :protocol "http:"})]
            (should= "ws://site.com/path?connection-id=conn-abc&ws-csrf-token=csrf-123"
                     (sut/build-local-uri location "/path" "conn-abc" "csrf-123"))))

        (it "secure"
          (let [location (clj->js {:host "site.com:443" :protocol "https:"})]
            (should= "wss://site.com:443/path2?connection-id=conn-xyz&ws-csrf-token=csrf-987"
                     (sut/build-local-uri location "/path2" "conn-xyz" "csrf-987"))))

        )

    (it "reconnects on close"
      (reset! sut/open? true)
      (sut/push-handler {:kind :ws/close})
      (should= false @sut/open?)
      (should-have-invoked :wsc/connect!))

    (it "first connection doesn't tell page reconnected"
      (reset! sut/open? false)
      (reset! sut/reconnection? false)
      (sut/push-handler {:kind :ws/open})
      (should= true @sut/open?)
      (should-not-have-invoked :reconnected!))

    (it "reconnection tells page when reconnected"
      (reset! sut/open? true)
      (reset! sut/reconnection? false)
      (sut/push-handler {:kind :ws/close})
      (sut/push-handler {:kind :ws/open})
      (should-have-invoked :reconnected!))

    )
  )
