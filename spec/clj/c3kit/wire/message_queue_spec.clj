(ns c3kit.wire.message-queue-spec
  (:require [c3kit.wire.message-queue :as sut]
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

       (it "message structure"
         (let [message# (promise)]
           (sut/on-message "foo" #(deliver message# %))
           (sut/enqueue "foo" "bar")
           (should= "foo" (:qname @message#))
           (should= "bar" (:message @message#))))

       )))

(describe "Message Queue"

  (test-message-queue {:impl :memory})

  (context "real"
    (with-stubs)

    ;(tags :slow)

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

      (it "automatically removes messages older than max-age"
        (sut/enqueue "foo" "blah")
        (sut/enqueue "bar" "blah")
        (Thread/sleep 25)
        (should= [{:qname "foo" :message "blah"}
                  {:qname "bar" :message "blah"}] (sut/messages))
        (Thread/sleep 100)
        (should-be empty? (sut/messages)))
      )
    )
  )
