(ns c3kit.wire.core.rest-spec
  (:require-macros [speclj.core :refer [describe it should= should-not-be-nil]])
  (:require [c3kit.wire.core.rest :as sut]
            [speclj.core]))

(describe "core rest — make-state"

  (it "make-state with no arg uses cljs.core/atom"
    (let [s (sut/make-state)]
      (should-not-be-nil (:active-requests s))
      (should= 0 @(:active-requests s))))

  (it "default-state shares active-reqs"
    (should= sut/active-reqs (:active-requests sut/default-state))))
