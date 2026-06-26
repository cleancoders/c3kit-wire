(ns c3kit.wire.service-worker-spec
  (:require-macros [speclj.core :refer [context describe it should should= should-be-nil should-not should-contain]])
  (:require [c3kit.wire.service-worker-fake :as fake]
            [c3kit.wire.service-worker :as sut]
            [speclj.core]))

(defn resolved-value [thenable]
  (let [out (atom ::none)]
    (.then thenable #(reset! out %))
    @out))

(describe "Service Worker fakes"
          (it "->resolved fires then synchronously and transforms"
              (should= 2 (resolved-value (.then (fake/->resolved 1) inc))))

          (it "->resolved flattens a returned thenable"
              (should= 3 (resolved-value (.then (fake/->resolved 1) (fn [_] (fake/->resolved 3))))))

          (it "->rejected skips then and is recovered by catch"
              (let [err (js/Error. "boom")]
                (should= :recovered (resolved-value (.catch (fake/->rejected err) (fn [_] :recovered))))))

          (it "fake cache stores and matches by request url"
              (let [cache (fake/->cache)
                    req   (fake/->request "https://app.test/a")
                    resp  (fake/->response "body")]
                (.put cache req resp)
                (should= resp (resolved-value (.match cache req)))))

          (it "fake cache addAll records urls"
              (let [cache (fake/->cache)]
                (.addAll cache #js ["https://app.test/x"])
                (should-contain "https://app.test/x" (js->clj (resolved-value (.keys cache))))))

          (it "fake caches opens named caches and lists/deletes them"
              (let [caches (fake/->caches)]
                (.open caches "one")
                (should-contain "one" (js->clj (resolved-value (.keys caches))))
                (should= true (resolved-value (.delete caches "one")))
                (should-not (resolved-value (.has caches "one")))))

          (it "fake-fetch resolves a response and can reject"
              (let [req (fake/->request "https://app.test/a")]
                (should= "ok" (.-body (resolved-value ((fake/fake-fetch (fake/->response "ok")) req))))
                (let [caught (atom nil)]
                  (.catch ((fake/fake-fetch :reject) req) #(reset! caught %))
                  (should= true (instance? js/Error @caught))))))

(describe "cacheable?"
          (let [ctx (fake/->ctx {:scope (fake/->scope {:origin "https://app.test"})})
                get-req (fake/->request "https://app.test/img.png")
                ok-resp (fake/->response "x")]

            (it "true for same-origin ok GET"
                (should= true (sut/cacheable? ctx get-req ok-resp {})))

            (it "false for non-GET"
                (should= false (sut/cacheable? ctx (fake/->request "https://app.test/x" {:method "POST"}) ok-resp {})))

            (it "false for non-ok response"
                (should= false (sut/cacheable? ctx get-req (fake/->response "x" {:ok false}) {})))

            (it "false for opaque response"
                (should= false (sut/cacheable? ctx get-req (fake/->response "x" {:type "opaque"}) {})))

            (it "false for opaqueredirect response"
                (should= false (sut/cacheable? ctx get-req (fake/->response "x" {:type "opaqueredirect"}) {})))

            (it "false for no-store response"
                (should= false (sut/cacheable? ctx get-req (fake/->response "x" {:headers {"Cache-Control" "no-store"}}) {})))

            (it "false for cross-origin by default, true when allowed"
                (let [x (fake/->request "https://cdn.other/x.png")]
                  (should= false (sut/cacheable? ctx x ok-resp {}))
                  (should= true  (sut/cacheable? ctx x ok-resp {:allow-cross-origin true}))))

            (it "false for credentialed by default, true when allowed"
                (let [c (fake/->request "https://app.test/x" {:credentials "include"})
                      a (fake/->request "https://app.test/x" {:headers {"Authorization" "Bearer t"}})]
                  (should= false (sut/cacheable? ctx c ok-resp {}))
                  (should= false (sut/cacheable? ctx a ok-resp {}))
                  (should= true  (sut/cacheable? ctx c ok-resp {:cache-credentialed true}))
                  (should= true  (sut/cacheable? ctx a ok-resp {:cache-credentialed true}))))))
