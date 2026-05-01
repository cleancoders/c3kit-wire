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
      (should= 0 @(:active-requests s))))

  (it "activity? reflects default-state's active-requests counter"
    (should= false (sut/activity?))
    (swap! sut/active-ajax-requests inc)
    (should= true (sut/activity?))
    (swap! sut/active-ajax-requests dec)
    (should= false (sut/activity?)))

  (it "default-state shares its active-requests atom with the active-ajax-requests def"
    (should= sut/active-ajax-requests (:active-requests sut/default-state))))
