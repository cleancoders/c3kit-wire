(ns c3kit.wire.lock
  (:import (java.util.concurrent.locks ReentrantLock)))

(defprotocol Lock
  (-shutdown [this] "Shuts down the lock")
  (-clear [this] "Clears out all locks")
  (-acquire [this key] "Acquires a lock associated with the key")
  (-release [this lock] "Releases a lock"))

(defmulti create :impl)

(defonce impl (atom nil))

(defn configure!
  "Creates and assigns the Lock implementation"
  [spec]
  (reset! impl (create spec)))

(defn clear!
  "Clears all keys from the Lock"
  [] (some-> @impl -clear))

(defn shutdown!
  "Shuts down the Lock"
  []
  (some-> @impl -shutdown)
  (reset! impl nil))

(defmacro with-lock [key & body]
  `(let [lock# (-acquire @impl ~key)]
     (try
       ~@body
       (finally
         (-release @impl lock#)))))

;region Memory

(defn- ensure-lock [locks key]
  (if (contains? locks key)
    locks
    (assoc locks key (ReentrantLock.))))

(defn- ensure-lock! [locks key]
  (-> (swap! locks ensure-lock key)
      (get key)))

(deftype MemoryLock [locks]
  Lock
  (-shutdown [this] (-clear this))
  (-clear [_this] (reset! locks {}))
  (-acquire [_this key] (doto (ensure-lock! locks key) .lock))
  (-release [_this lock] (.unlock lock)))

(defmethod create :memory [_]
  (->MemoryLock (atom {})))

;endregion
