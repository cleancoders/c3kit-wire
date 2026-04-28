(ns c3kit.wire.core.websocket-spec
  (:require-macros [speclj.core :refer [describe it should= should-not-be-nil]])
  (:require [c3kit.wire.core.websocket :as sut]
            [speclj.core]))

(describe "core websocket — make-state"

  (it "make-state with no arg uses cljs.core/atom"
    (let [s (sut/make-state)]
      (should-not-be-nil (:open? s))
      (should= false @(:open? s))))

  (it "default-state shares open?"
    (should= sut/open? (:open? sut/default-state))))
