(ns c3kit.wire.core.ajax-spec
  (:require-macros [speclj.core :refer [describe it should= should-not-be-nil]])
  (:require [c3kit.wire.core.ajax :as sut]
            [speclj.core]))

(describe "core ajax — make-state"

  (it "make-state with no arg uses cljs.core/atom"
    (let [s (sut/make-state)]
      (should-not-be-nil (:active-requests s))
      (should= 0 @(:active-requests s))))

  (it "make-state with custom atom-fn uses that atom-fn"
    (let [calls (atom [])
          my-atom-fn (fn [v] (swap! calls conj v) (atom v))
          s (sut/make-state my-atom-fn)]
      (should= [0] @calls)
      (should= 0 @(:active-requests s)))))
