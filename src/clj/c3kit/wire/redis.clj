(ns c3kit.wire.redis
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.time :as time]
            [c3kit.wire.message-queue :as mq])
  (:import (io.lettuce.core KeyScanArgs Range RedisClient RedisFuture ScanIterator StreamMessage XReadArgs$Builder XReadArgs$StreamOffset XTrimArgs)
           (io.lettuce.core.api StatefulRedisConnection)
           (io.lettuce.core.support ConnectionPoolSupport)
           (java.lang AutoCloseable)
           (org.apache.commons.pool2.impl GenericObjectPool GenericObjectPoolConfig)))

(defn- future-get [^RedisFuture redis-future] (.get redis-future))
(defmacro ^:private wait-for-all [bindings form]
  `(->> (ccc/for-all ~bindings ~form)
        (ccc/map-all future-get)))

(defmacro ^:private borrow-from [bindings & body]
  `(let [pool#     ~(bindings 1)
         borrowed# (.borrowObject ^GenericObjectPool pool#)
         ~(bindings 0) borrowed#]
     (try
       ~@body
       (finally (.close ^AutoCloseable borrowed#)))))

(defn- ^RedisClient -client [this] (.-client this))
(defn- ^GenericObjectPool -pool [this] (.-pool this))
(defn- running? [this] @(.-is-running this))

(defn- do-enqueue [this messages]
  (borrow-from [^StatefulRedisConnection conn (-pool this)]
    (let [commands (.async conn)]
      (wait-for-all [{:keys [qname message]} messages]
        (.xadd commands qname {"body" message})))))

(def ^:private key-scan-args (.type (KeyScanArgs.) "stream"))
(defn- all-keys [connection]
  (let [commands (.sync connection)
        iterator (ScanIterator/scan commands key-scan-args)]
    (->> (repeatedly #(when (.hasNext iterator) (.next iterator)))
         (take-while some?))))

(defn- StreamMessage->map [^StreamMessage message]
  {:id      (.getId message)
   :message (get (.getBody message) "body")
   :qname   (.getStream message)})

(defn- ->subscribe-handler [last-id subscribe-fn]
  (fn [^StreamMessage message]
    (let [message (StreamMessage->map message)]
      (reset! last-id (:id message))
      (subscribe-fn message))))

(defmacro ^:private thread-spawn [& body]
  `(doto (Thread. (fn [] ~@body)) .start))

(defn- read-from [this qname last-id]
  (let [offset  (XReadArgs$StreamOffset/from qname last-id)
        offsets (into-array [offset])
        args    (XReadArgs$Builder/block ^Long (:block-ms (.-config this)))]
    (borrow-from [^StatefulRedisConnection conn (-pool this)]
      (.xread (.sync conn) args offsets))))

(defn- ->handler-thread [this qname handler]
  (let [last-id    (atom (-> (time/now) time/millis-since-epoch dec str))
        handler-fn (->subscribe-handler last-id handler)]
    (thread-spawn
      (while (running? this)
        (->> (read-from this qname @last-id)
             (run! handler-fn))))))

(defn- do-on-message [this qname handler]
  (->> (->handler-thread this qname handler)
       (swap! (.-threads this) conj)))

(defn- trim-older-than [pool millis]
  (let [min-id (-> millis time/ago time/millis-since-epoch inc str)
        args   (.minId (XTrimArgs.) min-id)]
    (borrow-from [^StatefulRedisConnection conn pool]
      (let [commands (.async conn)]
        (wait-for-all [queue (all-keys conn)]
          (.xtrim commands queue args))))))

(defn- do-clear [this]
  (trim-older-than (-pool this) 0))

(defn- do-stop [this]
  (reset! (.-is-running this) false)
  (run! #(.join %) @(.-threads this))
  (.close (-pool this))
  (.shutdown (-client this)))

(defn- unbound-range [commands queue]
  (.xrange commands queue (Range/unbounded)))

(defn- sorted-messages [messages]
  (->> messages
       (map StreamMessage->map)
       (sort-by :id)))

(defn- do-read-queue-messages [this qname]
  (sorted-messages
    (borrow-from [^StatefulRedisConnection conn (-pool this)]
      (unbound-range (.sync conn) qname))))

(defn- collect-message-seqs [this]
  (borrow-from [^StatefulRedisConnection conn (-pool this)]
    (let [commands (.async conn)]
      (wait-for-all [queue (all-keys conn)]
        (unbound-range commands queue)))))

(defn- do-read-all-messages [this]
  (->> (collect-message-seqs this)
       (apply concat)
       sorted-messages))

(deftype RedisMessageQueue [is-running config threads client pool]
  mq/MessageQueue
  (-enqueue [this messages] (do-enqueue this messages))
  (-on-message [this qname handler] (do-on-message this qname handler))
  (-clear [this] (do-clear this))
  (-stop [this] (do-stop this))
  (-queue-messages [this qname] (do-read-queue-messages this qname))
  (-all-messages [this] (do-read-all-messages this)))

(defn- ->trim-task [is-running pool {:keys [max-age-ms block-ms]}]
  (thread-spawn
    (while @is-running
      (trim-older-than pool max-age-ms)
      (when (and @is-running (pos? block-ms))
        (Thread/sleep ^Long block-ms)))))

(defn- with-trim-task [is-running pool threads config]
  (when (pos? (:max-age-ms config))
    (->> (->trim-task is-running pool config)
         (swap! threads conj))))

(defn- ->RedisClient [uri]
  (RedisClient/create ^String uri))

(defn- ->redis-uri [{:keys [host port tls?]}]
  (let [protocol (str "redis" (when tls? "s"))]
    (str protocol "://" host ":" port)))

(defn- ->RedisConnectionPool [{:keys [client min-connections max-connections]}]
  (ConnectionPoolSupport/createGenericObjectPool
    #(.connect client)
    (doto (GenericObjectPoolConfig.)
      (.setMinIdle min-connections)
      (.setMaxTotal max-connections))))

(def default-config
  {:host            "localhost"
   :port            6379
   :min-connections 1
   :max-connections 3
   :max-age-ms      (time/seconds 5)
   :block-ms        (time/seconds 1)
   })

(defn ->RedisMessageQueue
  "Creates a MessageQueue utilizing Redis

   options:
     host            - The host of the Redis service
     port            - The port of the Redis service
     min-connections - The minimum number of connections to hold
     max-connections - The maximum number of connections to hold
     max-age-ms      - The amount of time a message may live for
     block-ms        - The maximum amount of time a connection may be held during a read"
  [spec]
  (let [config     (merge default-config spec)
        client     (->RedisClient (->redis-uri config))
        config     (assoc config :client client)
        pool       (->RedisConnectionPool config)
        is-running (atom true)
        threads    (atom #{})]
    (with-trim-task is-running pool threads config)
    (RedisMessageQueue. is-running config threads client pool)))

(defmethod mq/create :redis [spec] (->RedisMessageQueue spec))
