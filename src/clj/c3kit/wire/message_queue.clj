(ns c3kit.wire.message-queue
  (:require [c3kit.apron.corec :as ccc]))

(defprotocol MessageQueue
  (-enqueue [this messages]
    "Adds a sequence of message to the queue")
  (-on-message [this qname handler]
    "Attaches an event handler to the queue")
  (-clear [this] "Clears all messages and queues")
  (-stop [this] "Shuts down the message queue")
  (-queue-messages [this qname]
    "Returns a list of messages sent to the queue.")
  (-all-messages [this] "Returns a list of messages sent to any queue."))

(defmulti create :impl)

(defonce impl (atom nil))

(defn configure!
  "Creates a message queue according to spec and sets it as the implementation"
  [spec]
  (reset! impl (create spec)))

(defn clear!
  "Clears the message queue of all messages and queues"
  []
  (-clear @impl))

(defn shutdown!
  "Shuts down the configured message queue"
  []
  (swap! impl #(do (-stop %) nil)))

(defn enqueue
  "Adds a new message to the queue"
  ([qname message] (enqueue [{:qname qname :message message}]))
  ([messages] (-enqueue @impl messages)))

(defn on-message
  "Invokes handler any time a message is added to the queue"
  [qname handler]
  (-on-message @impl qname handler))

(defn- conform-message [m]
  (select-keys m [:qname :message]))

(defn messages
  "Adds a new message to the queue"
  ([] (map conform-message (-all-messages @impl)))
  ([qname] (map conform-message (-queue-messages @impl qname))))

;region Memory

(deftype InMemoryMessageQueue [messages handlers]
  MessageQueue
  (-stop [_this])
  (-enqueue [_this new-messages]
    (doseq [payload new-messages]
      (swap! messages conj payload)
      (run! #(% payload) (get @handlers (:qname payload)))))
  (-on-message [_this qname handler]
    (swap! handlers update qname conj handler))
  (-clear [_this] (reset! messages []))
  (-queue-messages [_this qname] (ccc/find-by @messages :qname qname))
  (-all-messages [_this] @messages))

(defmethod create :memory [_]
  (InMemoryMessageQueue. (atom []) (atom {})))

;endregion
