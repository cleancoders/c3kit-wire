(ns c3kit.wire.lock-spec
  (:require [c3kit.wire.lock :as lock]
            [c3kit.wire.lock :as sut]
            [c3kit.wire.redis]
            [speclj.core :refer :all]))

(defmacro test-lock [spec]
  `(let [spec# ~spec]
     (describe (name (:impl spec#))
       (before (sut/configure! spec#)
               (sut/clear!))
       (after (sut/clear!)
              (sut/shutdown!))

       (it "executes body with lock acquired"
         (let [executed?# (atom false)]
           (sut/with-lock "test-key"
             (reset! executed?# true))
           (should= true @executed?#)))

       (it "blocks concurrent access to the same key"
         (let [result#   (atom [])
               expected# [[:start1 :end1 :start2 :end2]
                          [:start2 :end2 :start1 :end1]]
               f1#       (future
                           (sut/with-lock "key"
                             (swap! result# conj :start1)
                             (Thread/sleep 100)
                             (swap! result# conj :end1)))
               f2#       (future
                           (sut/with-lock "key"
                             (swap! result# conj :start2)
                             (Thread/sleep 100)
                             (swap! result# conj :end2)))]
           @f1# @f2#
           (should-contain @result# expected#)))

       (it "allows concurrent access to different keys"
         (let [result# (atom [])
               f1#     (future
                         (sut/with-lock "key1"
                           (swap! result# conj :start1)
                           (Thread/sleep 100)
                           (swap! result# conj :end1)))
               f2#     (future
                         (sut/with-lock "key2"
                           (swap! result# conj :start2)
                           (Thread/sleep 100)
                           (swap! result# conj :end2)))]
           @f1# @f2#
           (should= 4 (count @result#))
           (should= #{:start1 :start2} (set (take 2 @result#)))
           (should= #{:end1 :end2} (set (drop 2 @result#)))))

       (it "clears all locks"
         (let [lock#      (lock/create {:impl :redis})
               executed?# (atom nil)]
           (lock/-acquire lock# "foo")
           (lock/-acquire lock# "bar")
           (lock/-acquire lock# "baz")
           (lock/-clear lock#)
           (lock/with-lock "foo"
             (lock/with-lock "bar"
               (lock/with-lock "baz"
                 (reset! executed?# true))))
           (should= true @executed?#)))

       )))

(describe "Lock"

  (test-lock {:impl :memory})

  (context "real"

    (tags :slow)

    (test-lock {:impl :redis})

    )
  )