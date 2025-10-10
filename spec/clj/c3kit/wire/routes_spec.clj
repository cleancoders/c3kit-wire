(ns c3kit.wire.routes-spec
  (:require [c3kit.apron.util :as util]
            [c3kit.wire.routes :as sut]
            [c3kit.wire.spec-helper :as helper]
            [compojure.core :as compojure]
            [ring.util.response :as response]
            [speclj.core :refer :all]))


(def prefix-handler (-> (compojure/routes (compojure/ANY "/bar" [] "bar")
                                          (compojure/ANY "/fizz" [] "fizz"))
                        (sut/wrap-prefix "/foo" (fn [_] (response/not-found "nope")))))

(describe "Routes wrap-prefix"

  (it "ignores request that don't match prefix"
    (should= nil (prefix-handler {:uri "/blah/bar"}))
    (should= nil (prefix-handler {:uri "/"})))

  (it "shortens path for sub-handlers"
    (should= "bar" (-> (prefix-handler {:uri "/foo/bar"}) :body))
    (should= "fizz" (-> (prefix-handler {:uri "/foo/fizz"}) :body)))

  (it "bails with not-found handler with no match"
    (let [response (prefix-handler {:uri "/foo/bang"})]
      (should= 404 (:status response))
      (should= "nope" (:body response))))

  (it "respects path-info"
    (should= "bar" (-> (prefix-handler {:uri "/blah" :path-info "/foo/bar"}) :body))
    (should= "nope" (-> (prefix-handler {:uri "/blah" :path-info "/foo/blah"}) :body)))

  )


(defn fizz-bang [_] "fizz-bang")
(defn zig-zag [_] "zig-zag")

(def lazy-handler (sut/lazy-routes {["/fizz/bang" :any] c3kit.wire.routes-spec/fizz-bang
                                    ["/zig/zag" :any]   c3kit.wire.routes-spec/zig-zag}))

(describe "Routes lazy-routes"

  (with-stubs)
  (before (reset! sut/reload? false))
  (redefs-around [util/resolve-var (stub :resolve-var {:invoke util/resolve-var})
                  sut/-memoized-resolve-handler (memoize sut/-resolve-handler)])

  (it "loads handlers"
    (should= "fizz-bang" (:body (lazy-handler {:uri "/fizz/bang"})))
    (should= "zig-zag" (:body (lazy-handler {:uri "/zig/zag"}))))

  (it "only loads handler once"
    (lazy-handler {:uri "/fizz/bang"})
    (lazy-handler {:uri "/fizz/bang"})
    (should-have-invoked :resolve-var {:times 1}))

  (it "loads handler each time when reload? set to true"
    (reset! sut/reload? true)
    (lazy-handler {:uri "/fizz/bang"})
    (lazy-handler {:uri "/fizz/bang"})
    (should-have-invoked :resolve-var {:times 2}))

  )

(def redirect-handler (sut/redirect-routes {["/old/one" :any]        "/new/one"
                                            ["/old/:thing/two" :any] "/new/:thing/two"}))

(describe "Routes redirect-routes"

  (it "simple routes"
    (let [response (redirect-handler {:uri "/old/one"})]
      (helper/should-redirect-to response "/new/one")))

  (it "routes with bounded parameters"
    (let [response (redirect-handler {:uri "/old/blah/two"})]
      (helper/should-redirect-to response "/new/blah/two")))

  )
