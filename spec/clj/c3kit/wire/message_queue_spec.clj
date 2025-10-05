(ns c3kit.wire.message-queue-spec
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.message-queue :as sut]
            [c3kit.wire.redis :as redis]
            [speclj.core :refer :all]))

(defn with-impl [spec]
  (list
    (before (sut/configure! spec)
            (sut/clear!))
    (after (sut/clear!)
           (sut/shutdown!))))

(defmacro test-message-queue [spec]
  `(let [spec# ~spec]
     (context (:impl spec#)
       (with-impl spec#)

       (it "no messages"
         (should-be empty? (sut/messages)))

       (it "one message"
         (sut/enqueue "foo" "bar")
         (should= [(sut/->message "foo" "bar")] (sut/messages)))

       (it "two messages"
         (sut/enqueue "foo" "bar")
         ; Add some delta for sorting by id
         (Thread/sleep 1)
         (sut/enqueue "baz" "buzz")
         (should= [(sut/->message "foo" "bar")] (sut/messages "foo"))
         (should= [(sut/->message "baz" "buzz")] (sut/messages "baz"))
         (should= [(sut/->message "foo" "bar")
                   (sut/->message "baz" "buzz")] (sut/messages)))

       (it "clears all messages and queues"
         (sut/enqueue "foo" "bar")
         (sut/enqueue "baz" "buzz")
         (sut/clear!)
         (should-be empty? (sut/messages)))

       (it "message handler"
         (let [result#   (atom "")
               flushed?# (promise)]
           (sut/on-message "foo" (fn [m#]
                                   (let [message# (:message m#)]
                                     (swap! result# str message#)
                                     (when (= "buzz" message#)
                                       (deliver flushed?# nil)))))
           (sut/enqueue "foo" "baz")
           (sut/enqueue "bar" "foo")
           (sut/enqueue "foo" "buzz")
           @flushed?#
           (should= "bazbuzz" @result#)))

       (it "many messages at once"
         (let [result#   (atom "")
               flushed?# (promise)]
           (sut/on-message "foo" (fn [m#]
                                   (let [message# (:message m#)]
                                     (swap! result# str message#)
                                     (when (= "buzz" message#)
                                       (deliver flushed?# nil)))))
           (sut/enqueue
             [(sut/->message "foo" "baz")
              (sut/->message "bar" "foo")
              (sut/->message "foo" "buzz")])
           @flushed?#
           (should= "bazbuzz" @result#)))

       (it "two message handlers"
         (let [foo-result#    (atom "")
               bar-result#    (atom "")
               buzz-flushed?# (promise)
               bar-flushed?#  (promise)]
           (sut/on-message "foo" (fn [m#]
                                   (let [message# (:message m#)]
                                     (swap! foo-result# str message#)
                                     (when (= "buzz" message#)
                                       (deliver buzz-flushed?# nil)))))
           (sut/on-message "bar" #(->> (swap! bar-result# str (:message %))
                                       (deliver bar-flushed?#)))
           (sut/enqueue "foo" "baz")
           (sut/enqueue "bar" "foo")
           (sut/enqueue "foo" "buzz")
           @buzz-flushed?#
           @bar-flushed?#
           (should= "bazbuzz" @foo-result#)
           (should= "foo" @bar-result#)))

       (it "two message handlers - one throws"
         (let [result#   (atom "")
               flushed?# (promise)
               ex#       (ex-info "blah" {})]
           (sut/on-message "foo" (fn [_m#] (throw ex#)))
           (sut/on-message "foo" (fn [m#]
                                   (let [message# (:message m#)]
                                     (swap! result# str message#)
                                     (when (= "buzz" message#)
                                       (deliver flushed?# nil)))))
           (sut/enqueue "foo" "baz")
           (sut/enqueue "foo" "buzz")
           @flushed?#
           (should= "bazbuzz" @result#)
           (should-contain (str ex#) (log/captured-logs-str))))

       (it "message structure"
         (let [message# (promise)]
           (sut/on-message "foo" #(deliver message# %))
           (sut/enqueue "foo" "bar")
           (should= "foo" (:qname @message#))
           (should= "bar" (:message @message#))))

       )))

(defn interrupted? [^Thread t] (.isInterrupted t))

(describe "Message Queue"
  (around [it] (log/capture-logs (it)))

  (test-message-queue {:impl :memory})

  (context "real"
    (with-stubs)

    (tags :slow)

    (test-message-queue {:impl       :redis
                         :max-age-ms 0
                         :block-ms   100})

    (it "secure client"
      (with-redefs [redis/->RedisClient (stub :->RedisClient)]
        (sut/create {:impl       :redis
                     :max-age-ms 0
                     :block-ms   0
                     :tls?       true})
        (should-have-invoked :->RedisClient {:with ["rediss://localhost:6379"]})))

    (it "insecure client"
      (with-redefs [redis/->RedisClient (stub :->RedisClient)]
        (sut/create {:impl       :redis
                     :max-age-ms 0
                     :block-ms   0})
        (should-have-invoked :->RedisClient {:with ["redis://localhost:6379"]})))

    (context "impl"

      (with-impl {:impl       :redis
                  :max-age-ms 100
                  :block-ms   50})

      (it "throws on read"
        (let [errors      (atom 0)
              redis-xread redis/xread]
          (letfn [(mock-xread [conn args offset]
                    (when (zero? @errors)
                      (swap! errors inc)
                      (throw (ex-info "blah" {})))
                    (redis-xread conn args offset))]
            (with-redefs [redis/xread mock-xread]
              (let [delivered (promise)]
                (sut/on-message "foo" #(deliver delivered %))
                (sut/enqueue "foo" "blah")
                @delivered
                (should-be realized? delivered))))))

      (it "interrupts read thread on interrupt exceptions"
        (let [errors  (atom 0)
              thrown? (promise)]
          (letfn [(mock-xread [_commands _args _offset]
                    (when (zero? @errors)
                      (swap! errors inc)
                      (deliver thrown? nil)
                      (throw (InterruptedException.))))]
            (with-redefs [redis/xread mock-xread]
              (sut/on-message "foo" ccc/noop)
              (sut/enqueue "foo" "blah")
              @thrown?
              (should= 1 (ccc/count-where interrupted? @(.-threads @sut/impl)))))))

      (it "automatically removes messages older than max-age"
        (sut/enqueue "foo" "blah")
        (sut/enqueue "bar" "blah")
        (Thread/sleep 25)
        (should= 2 (count (sut/messages)))
        (Thread/sleep 100)
        (should-be empty? (sut/messages)))

      (it "throws on trim task"
        (let [errors        (atom 0)
              error-thrown? (promise)
              redis-xtrim   redis/xtrim
              ex            (ex-info "blah" {})]
          (letfn [(mock-xtrim [commands queue args]
                    (when (zero? @errors)
                      (swap! errors inc)
                      (deliver error-thrown? nil)
                      (throw ex))
                    (redis-xtrim commands queue args))]
            (with-redefs [redis/xtrim mock-xtrim]
              (sut/enqueue "foo" "blah")
              @error-thrown?
              (should= [(sut/->message "foo" "blah")] (sut/messages))
              (Thread/sleep 100)
              (should= (str ex) (log/captured-logs-str))
              (should-be empty? (sut/messages))))))

      (it "interrupts trim thread on interrupt exception"
        (let [errors  (atom 0)
              thrown? (promise)]
          (letfn [(mock-xtrim [_commands _queue _args]
                    (when (zero? @errors)
                      (swap! errors inc)
                      (deliver thrown? nil)
                      (throw (InterruptedException.))))]
            (with-redefs [redis/xtrim mock-xtrim]
              (sut/enqueue "foo" "blah")
              @thrown?
              (Thread/sleep 10)
              (should= 1 (ccc/count-where interrupted? @(.-threads @sut/impl)))))))
      )
    )
  )
