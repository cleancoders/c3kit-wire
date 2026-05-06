(ns c3kit.wire.core.rest-spec
  (:require-macros [speclj.core :refer [describe it should should-not should= should-not-be-nil]])
  (:require [c3kit.wire.core.rest :as sut]
            [speclj.core]))

(describe "core rest — make-state"

  (it "make-state with no arg uses cljs.core/atom"
    (let [s (sut/make-state)]
      (should-not-be-nil (:active-requests s))
      (should= 0 @(:active-requests s))))

  (it "default-state shares active-reqs"
    (should= sut/active-reqs (:active-requests sut/default-state)))

  (it "activity? reflects active-reqs counter"
    (should= false (sut/activity?))
    (swap! sut/active-reqs inc)
    (should= true (sut/activity?))
    (swap! sut/active-reqs dec)
    (should= false (sut/activity?))))

(describe "core rest — response predicates"

  (it "success?"
    (should (sut/success? {:status 200}))
    (should (sut/success? {:status 299}))
    (should-not (sut/success? {:status 199}))
    (should-not (sut/success? {:status 300}))
    (should-not (sut/success? {:status 404})))

  (it "error?"
    (should (sut/error? {:status 400}))
    (should (sut/error? {:status 500}))
    (should (sut/error? {:status 600}))
    (should-not (sut/error? {:status 399}))
    (should-not (sut/error? {:status 200})))

  (it "bad-req?"
    (should (sut/bad-req? {:status 400}))
    (should-not (sut/bad-req? {:status 401})))

  (it "unauthenticated?"
    (should (sut/unauthenticated? {:status 401}))
    (should-not (sut/unauthenticated? {:status 403})))

  (it "unauthorized?"
    (should (sut/unauthorized? {:status 403}))
    (should-not (sut/unauthorized? {:status 401})))

  (it "not-found?"
    (should (sut/not-found? {:status 404}))
    (should-not (sut/not-found? {:status 400})))

  (it "server-error?"
    (should (sut/server-error? {:status 500}))
    (should (sut/server-error? {:status 599}))
    (should-not (sut/server-error? {:status 499}))))
