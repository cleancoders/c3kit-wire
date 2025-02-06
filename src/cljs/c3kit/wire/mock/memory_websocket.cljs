(ns c3kit.wire.mock.memory-websocket
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.mock.event :as event]
            [c3kit.wire.mock.server :as server]
            [c3kit.wire.socket :as sock]))

(defn- encode-utf8
  "Encode a string in UTF-8"
  [s]
  (js-invoke (js/TextEncoder.) "encode" s))

(defn- syntax-error! [message data]
  (throw (js/SyntaxError. (str message ": " data))))

(defn- select-protocol [protocols]
  (let [protocols (js->clj protocols)]
    (cond (string? protocols) protocols
          (not (sequential? protocols))
          (syntax-error! "Protocols must be a string or a collection" protocols)
          (not= (count protocols) (count (distinct protocols)))
          (syntax-error! "Protocols may not contain duplicates" protocols)
          :else (or (first protocols) ""))))

(defn- assert-valid-url [url]
  (when-not (re-find #"^wss?://[^#]*$" url)
    (syntax-error! "URL is invalid" url)))

(defn- assert-short-reason [reason]
  (when (< 123 (ccc/oget (encode-utf8 reason) "length"))
    (throw (ex-info "Reason must be less than 123 bytes" reason))))

(defn- assert-closure-code [code]
  (when-not (or (= 1000 code) (<= 3000 code 4999))
    (throw (ex-info "Code must be either 1000 or between 3000 and 4999" code))))

(defn- send [data]
  (this-as sock
    (case (sock/ready-state sock)
      0 (throw (ex-info "MemSocket is still in CONNECTING state." data))
      1 (server/receive sock data)
      (log/error "MemSocket is already in CLOSING or CLOSED state."))))

(defn- on-close [sock code reason]
  (wjs/dispatch-event sock (event/->CloseEvent sock code reason true)))

(defn- close
  ([sock] (close sock 1000))
  ([sock code]
   (assert-closure-code code)
   (on-close sock code "")
   (ccc/oset sock "readyState" 3)
   (log/info (str "Closing MemSocket with code: " code)))
  ([sock code reason]
   (assert-closure-code code)
   (assert-short-reason reason)
   (ccc/oset sock "readyState" 3)
   (on-close sock code reason)
   (log/info (str "Closing MemSocket with code: " code))
   (log/info (str "Closing MemSocket with reason: " reason))))

(defn- add-slash? [url] (not (re-find #"^wss?://[^/]*/" url)))
(defn- normalize-url [url] (cond-> url (add-slash? url) (str "/")))

(defn- add-listener [event-queue event listener]
  (when (not-any? #{listener} (get @event-queue event))
    (swap! event-queue update event ccc/conjv listener)))

(defn- remove-listener [event-queue event listener]
  (swap! event-queue update event #(ccc/removev #{listener} %)))

(defn- attempt-handler [event handler]
  (try
    (handler event)
    (catch :default e
      (let [event-type (ccc/oget event "type")]
        (when (= "close" event-type) (ccc/oset event "wasClean" false))
        (log/error (str "Error occurred in MemSocket " event-type) e)))))

(defn- dispatch-event [event-queue event]
  (let [event-type (ccc/oget event "type")
        handlers   (seq (get @event-queue event-type))]
    (run! #(attempt-handler event %) handlers)))

(defn- create-memory-socket [url protocols]
  (assert-valid-url url)
  (let [event-queue (atom {})]
    (js-obj
      "binaryType" "blob"
      "bufferedAmount" 0
      "extensions" ""
      "protocol" (select-protocol protocols)
      "readyState" 0
      "url" (normalize-url url)
      "addEventListener" (fn [event listener] (add-listener event-queue event listener))
      "removeEventListener" (fn [event listener] (remove-listener event-queue event listener))
      "dispatchEvent" (fn [event] (dispatch-event event-queue event))
      "send" send
      "close" (fn
                ([] (this-as this (close this)))
                ([code] (this-as this (close this code)))
                ([code reason] (this-as this (close this code reason)))))))

(defn ->MemSocket
  ([url] (->MemSocket url (clj->js [])))
  ([url protocols]
   (assert-valid-url url)
   (doto (create-memory-socket url protocols)
     server/initiate)))
