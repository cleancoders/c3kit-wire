(ns c3kit.wire.lock
  (:import (java.util.concurrent.locks ReentrantLock)))

(defprotocol Lock
  (-shutdown [this] "Shuts down the lock")
  (-clear [this] "Clears all locks")
  (-acquire [this key] "Acquires a lock associated with the key")
  (-release [this key] "Releases the lock associated with the key"))

(defmulti create-lock :impl)

(defonce impl (atom nil))

(defn configure! [spec]
  (reset! impl (create-lock spec)))

(defn clear! [] (-clear @impl))
(defn shutdown! [] (-shutdown @impl))

(defmacro with-lock [key & body]
  `(let [key# ~key]
     (-acquire @impl key#)
     (try
       ~@body
       (finally
         (-release @impl key#)))))

;region Memory

(defn- ^ReentrantLock ensure-lock [locks key]
  (or (get @locks key)
      (let [lock (ReentrantLock.)]
        (swap! locks assoc key lock)
        lock)))

(deftype MemoryLock [locks]
  Lock
  (-shutdown [this] (-clear this))
  (-clear [_this] (reset! locks {}))
  (-acquire [_this key]
    (doto (ensure-lock locks key) .lock))
  (-release [_this key]
    (some-> ^ReentrantLock (get @locks key) .unlock)))

(defmethod create-lock :memory [_]
  (->MemoryLock (atom {})))

;endregion
