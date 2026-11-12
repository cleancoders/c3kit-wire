(ns c3kit.wire.redis
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.time :as time]
            [clojure.string :as str]
            [c3kit.wire.lock :as lock]
            [c3kit.wire.message-queue :as mq])
  (:import (io.lettuce.core KeyScanArgs Range RedisClient RedisFuture ScanIterator StreamMessage XReadArgs$Builder XReadArgs$StreamOffset XTrimArgs)
           (io.lettuce.core.api StatefulRedisConnection)
           (io.lettuce.core.support ConnectionPoolSupport)
           (java.lang AutoCloseable)
           (org.apache.commons.pool2.impl GenericObjectPool GenericObjectPoolConfig)
           (org.redisson Redisson)
           (org.redisson.config Config)))

(def default-config
  {:host            "localhost"
   :port            6379
   :min-connections 1
   :max-connections 3
   :max-age-ms      (time/seconds 5)
   :block-ms        (time/seconds 1)})

(defn- ->redis-uri [{:keys [host port tls?]}]
  (let [protocol (str "redis" (when tls? "s"))]
    (str protocol "://" host ":" port)))

(defn- escape-glob
  "Escapes Redis glob metacharacters so s matches only itself in a SCAN/KEYS pattern."
  [s]
  (str/replace s #"[\\*?\[\]]" "\\\\$0"))

(defn- prefix-pattern
  "A glob pattern matching every key that begins with prefix."
  [prefix]
  (str (escape-glob (or prefix "")) "*"))

;region Message Queue

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

(defn- -client ^RedisClient [this] (.-client this))
(defn- -pool ^GenericObjectPool [this] (.-pool this))
(defn- running? [this] @(.-is-running this))
(defn- -prefix [this] (:prefix (.-config this)))

(defn- ->key
  "The Redis stream key for qname: the queue's prefix followed by qname."
  [prefix qname]
  (str prefix qname))

(defn- ->qname
  "Inverse of ->key: strips prefix from a Redis stream key."
  [prefix key]
  (if (and (seq prefix) (str/starts-with? key prefix))
    (subs key (count prefix))
    key))

(defn- do-enqueue [this messages]
  (let [prefix (-prefix this)]
    (borrow-from [^StatefulRedisConnection conn (-pool this)]
                 (let [commands (.async conn)]
                   (wait-for-all [{:keys [qname message]} messages]
                                 (.xadd commands (->key prefix qname) {"body" message}))))))

(defn- ->key-scan-args
  "Scans stream keys only; with a prefix, only keys beginning with it."
  [prefix]
  (let [args (KeyScanArgs.)]
    (.type args "stream")
    (when (seq prefix)
      (.match args ^String (prefix-pattern prefix)))
    args))

(defn- all-keys [connection prefix]
  (let [commands (.sync connection)
        iterator (ScanIterator/scan commands (->key-scan-args prefix))]
    (->> (repeatedly #(when (.hasNext iterator) (.next iterator)))
         (take-while some?))))

(defn- StreamMessage->map [prefix ^StreamMessage message]
  {:id      (.getId message)
   :message (get (.getBody message) "body")
   :qname   (->qname prefix (.getStream message))})

(defmacro ^:private thread-spawn [& body]
  `(doto (Thread. (fn [] ~@body)) .start))

(defn xread [commands args offset]
  (.xread commands args offset))

(defn xtrim [commands queue args]
  (.xtrim commands queue args))

(defn- read-from [this qname last-id]
  (let [offset  (XReadArgs$StreamOffset/from (->key (-prefix this) qname) last-id)
        offsets (into-array [offset])
        args    (XReadArgs$Builder/block ^Long (:block-ms (.-config this)))]
    (borrow-from [^StatefulRedisConnection conn (-pool this)]
                 (xread (.sync conn) args offsets))))

(defn- flush-message [handler last-id payload]
  (reset! last-id (:id payload))
  (mq/with-error-handling
    (handler payload)))

(defn- flush-messages [this qname handler last-id]
  (mq/with-error-handling
    (->> (read-from this qname @last-id)
         (map (partial StreamMessage->map (-prefix this)))
         (run! (partial flush-message handler last-id)))))

(defn- ->handler-thread [this qname handler]
  (let [last-id (atom (-> (time/now) time/millis-since-epoch dec str))]
    (thread-spawn
      (while (running? this)
        (flush-messages this qname handler last-id)))))

(defn- do-on-message [this qname handler]
  (->> (->handler-thread this qname handler)
       (swap! (.-threads this) conj)))

(defn- trim-older-than [pool prefix millis]
  (let [min-id (-> millis time/ago time/millis-since-epoch inc str)
        args   (.minId (XTrimArgs.) min-id)]
    (borrow-from [^StatefulRedisConnection conn pool]
                 (let [commands (.async conn)]
                   (wait-for-all [queue (all-keys conn prefix)]
                                 (xtrim commands queue args))))))

(defn- do-clear [this]
  (trim-older-than (-pool this) (-prefix this) 0))

(defn- do-stop [this]
  (reset! (.-is-running this) false)
  (run! #(.join %) @(.-threads this))
  (.close (-pool this))
  (.shutdown (-client this)))

(defn- unbound-range [commands queue]
  (.xrange commands queue (Range/unbounded)))

(defn- sorted-messages [prefix messages]
  (->> messages
       (map (partial StreamMessage->map prefix))
       (sort-by :id)))

(defn- do-read-queue-messages [this qname]
  (let [prefix (-prefix this)]
    (sorted-messages
      prefix
      (borrow-from [^StatefulRedisConnection conn (-pool this)]
                   (unbound-range (.sync conn) (->key prefix qname))))))

(defn- collect-message-seqs [this]
  (borrow-from [^StatefulRedisConnection conn (-pool this)]
               (let [commands (.async conn)]
                 (wait-for-all [queue (all-keys conn (-prefix this))]
                               (unbound-range commands queue)))))

(defn- do-read-all-messages [this]
  (->> (collect-message-seqs this)
       (apply concat)
       (sorted-messages (-prefix this))))

(deftype RedisMessageQueue [is-running config threads client pool]
  mq/MessageQueue
  (-enqueue [this messages] (do-enqueue this messages))
  (-on-message [this qname handler] (do-on-message this qname handler))
  (-clear [this] (do-clear this))
  (-stop [this] (do-stop this))
  (-queue-messages [this qname] (do-read-queue-messages this qname))
  (-all-messages [this] (do-read-all-messages this)))

(defn- ->trim-task [is-running pool {:keys [prefix max-age-ms block-ms]}]
  (thread-spawn
    (while @is-running
      (mq/with-error-handling
        (trim-older-than pool prefix max-age-ms)
        (when (and @is-running (pos? block-ms))
          (Thread/sleep ^Long block-ms))))))

(defn- with-trim-task [is-running pool threads config]
  (when (pos? (:max-age-ms config))
    (->> (->trim-task is-running pool config)
         (swap! threads conj))))

(defn- ->RedisClient [uri]
  (RedisClient/create ^String uri))

(defn- ->RedisConnectionPool [{:keys [client min-connections max-connections]}]
  (ConnectionPoolSupport/createGenericObjectPool
    #(.connect client)
    (doto (GenericObjectPoolConfig.)
      (.setMinIdle min-connections)
      (.setMaxTotal max-connections))))

;; Intentionally shadows the deftype-generated positional ctor with a
;; spec-based one — this is the public constructor.
#_{:clj-kondo/ignore [:redefined-var]}
(defn ->RedisMessageQueue
  "Creates a MessageQueue utilizing Redis

   options:
     host            - The host of the Redis service
     port            - The port of the Redis service
     tls?            - When true, uses a secure connection
     prefix          - Optional string prepended to every queue's Redis key, e.g. \"poker:\".
                       Lets several apps share one Redis: each app only reads, trims and
                       clears keys under its own prefix. Handlers still see unprefixed qnames.
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

;endregion

;region Lock

(def ^:private lock-prefix "c3kit-wire-redis-lock-")
(defn- ->keyname [prefix key] (str prefix lock-prefix key))

(deftype RedisLock [^Redisson client prefix]
  lock/Lock
  (-shutdown [_this] (.shutdown client))
  (-clear [_this] (.deleteByPattern (.getKeys client) (prefix-pattern (->keyname prefix ""))))
  (-acquire [_this key] (doto (.getLock client (->keyname prefix key)) .lock))
  (-release [_this lock] (.unlock lock)))

(defn- ->RedissonClient [uri]
  (let [config (Config.)]
    (-> config .useSingleServer (.setAddress uri))
    (Redisson/create config)))

;; Intentionally shadows the deftype-generated positional ctor with a
;; spec-based one — this is the public constructor.
#_{:clj-kondo/ignore [:redefined-var]}
(defn ->RedisLock
  "Creates a Lock utilizing Redis

   options:
     host   - The host of the Redis service
     port   - The port of the Redis service
     tls?   - When true, uses a secure connection
     prefix - Optional string prepended to every lock's Redis key, e.g. \"poker:\".
              Lets several apps share one Redis: clearing only removes this prefix's locks."
  [spec]
  (let [config (merge default-config spec)]
    (RedisLock. (->RedissonClient (->redis-uri config)) (:prefix config))))

(defmethod lock/create :redis [spec] (->RedisLock spec))

;endregion
