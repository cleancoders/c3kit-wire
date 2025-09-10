(ns c3kit.wire.message-queue-spec
  (:require [c3kit.wire.message-queue :as sut]
            [c3kit.wire.redis]
            [speclj.core :refer :all]
            [taoensso.carmine.message-queue :as car-mq]))

(defmacro test-message-queue [spec]
  `(let [spec# ~spec]
     (context (:impl spec#)
       (before (sut/configure! spec#))
       (after (sut/clear!)
              (sut/shutdown!))

       (it "no messages"
         (should-be empty? (sut/messages)))

       (it "one message"
         (sut/enqueue "foo" "bar")
         (should= [(sut/->message "foo" "bar")] (sut/messages)))

       (it "two messages"
         (sut/enqueue "foo" "bar")
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
         (let [foo-result# (atom "")
               bar-result# (atom "")
               flushed?#   (promise)]
           (sut/on-message "foo" (fn [m#]
                                   (let [message# (:message m#)]
                                     (swap! foo-result# str message#)
                                     (when (= "buzz" message#)
                                       (deliver flushed?# nil)))))
           (sut/on-message "bar" #(swap! bar-result# str (:message %)))
           (sut/enqueue "foo" "baz")
           (sut/enqueue "bar" "foo")
           (sut/enqueue "foo" "buzz")
           @flushed?#
           (should= "bazbuzz" @foo-result#)
           (should= "foo" @bar-result#)))

       (it "message structure"
         (let [message# (promise)]
           (sut/on-message "foo" #(deliver message# %))
           (sut/enqueue "foo" "bar")
           (should= "foo" (:qname @message#))
           (should= "bar" (:message @message#))
           (should-be parse-uuid (:mid @message#))
           (should-be pos? (:attempt @message#))
           (should-be number? (:lock-ms @message#))
           (should-be number? (:age-ms @message#))
           (should-be number? (:queue-size @message#))))

       )))

(describe "Message Queue"

  (test-message-queue {:impl :memory})

  (context "real"

    ;(tags :slow)

    (before (car-mq/set-min-log-level! :report))

    (test-message-queue {:impl      :redis
                         :conn-opts {:spec {:host "127.0.0.1" :port 6379}
                                     :pool :none}})

    )

  )
