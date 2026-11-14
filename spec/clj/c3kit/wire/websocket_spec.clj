(ns c3kit.wire.websocket-spec
  (:require
    [c3kit.apron.app :as app]
    [c3kit.apron.log :as log]
    [c3kit.wire.api :as api]
    [c3kit.wire.flashc :as flashc]
    [c3kit.wire.websocket :as sut]
    [c3kit.wire.websocketc :as wsc]
    [speclj.core :refer :all]
    [speclj.stub :as stub]))

(defn on-close-foo [{:keys [connection-id]}] {:foo connection-id})
(def foo-handlers {:ws/close 'c3kit.wire.websocket-spec/on-close-foo})

(describe "websocket"

  (with-stubs)
  (before (api/configure! :ws-handlers nil :ws-transport nil :version "123")
          (sut/development!))
  (around [it] (log/capture-logs (it)))

  (it "invokes default handler by default"
    (let [response (sut/message-handler {:kind :blah/blah})]
      (should= :error (:status response))
      (should= "Unsupported websocket Call: :blah/blah" (-> response :flash first flashc/text)))
    (should-contain "Unhandled websocket event" (log/captured-logs-str)))

  (it "handles pings"
    (let [result (sut/message-handler {:kind :ws/ping})]
      (should= :pong (:payload result))
      (should= :ok (:status result)))
    (should-not-contain "Unhandled remote event" (log/captured-logs-str)))

  (it "handles uidport-open"
    (sut/message-handler {:kind :ws/open :connection-id "d195b25e-bbf9-4c78-909b-abadb57d7bb1"})
    (should-contain "websocket call: :ws/open" (log/captured-logs-str)))

  (it "default uidport-close handler"
    (sut/message-handler {:kind :ws/close :connection-id "uid123"})
    (should-contain "UNHANDLED websocket connection closed: uid123" (log/captured-logs-str)))

  (it "installed connection closed handler"
    (api/configure! :ws-handlers 'c3kit.wire.websocket-spec/foo-handlers)
    (let [response (sut/message-handler {:kind :ws/close :connection-id "uid123"})]
      (should= "uid123" (:foo response)))
    (should-not-contain "UNHANDLED websocket connection closed: uid123" (log/captured-logs-str)))

  (it "includes the version"
    (let [response (sut/message-handler {:id :chsk/ws-ping})]
      (should= "123" (:version response))))

  (context "service"

    (around [it] (with-redefs [app/development? (stub :development? {:return false})
                               wsc/create        (stub :wsc/create {:return :a-server})]
                   (it)))

    (it "start with default transport"
      (let [app (sut/start {})]
        (should= :a-server (:ws/server app))
        (should= [sut/message-handler] (stub/last-invocation-of :wsc/create))))

    (it "start with configured transport"
      (let [transport {:open :a-open :send! :a-send :close :a-close}]
        (api/configure! :ws-transport transport)
        (sut/start {})
        (should= [sut/message-handler :transport transport] (stub/last-invocation-of :wsc/create))))

    (it "stop removes the server"
      (should-not-contain :ws/server (sut/stop {:ws/server :a-server}))))

  (context "reject"

    (around [it] (with-redefs [app/app    (assoc app/app :ws/server :a-server)
                               wsc/reject (stub :wsc/reject {:return :a-response})]
                   (it)))

    (it "delegates to the started server"
      (should= :a-response (sut/reject {:websocket? true}))
      (should-have-invoked :wsc/reject {:with [:a-server {:websocket? true} nil]}))

    (it "with a response for plain requests"
      (should= :a-response (sut/reject {:uri "/ws"} {:status 404}))
      (should-have-invoked :wsc/reject {:with [:a-server {:uri "/ws"} {:status 404}]})))

  ;(it "handles :ws/close"
  ;  (with-redefs [ws/dispatch-closed-connection (stub :dispatch-closed-connection {:return {}})
  ;                ws/close! (stub :close!)]
  ;    (ws/message-handler {:kind :ws/close :connection-id "uid123"}))
  ;  (should-have-invoked :dispatch-closed-connection {:with ["uid123"]})
  ;  (should-have-invoked :close! {:with ["uid123"]}))
  )
