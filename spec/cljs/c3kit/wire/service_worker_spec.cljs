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

(defn store-keys [cache] (js->clj (resolved-value (.keys cache))))
(defn open-cache [ctx name] (resolved-value (.open (:caches ctx) name)))

(describe "fallback + cache-response!"
          (it "->fallback returns a 503 by default"
              (should= 503 (.-status (sut/->fallback {} (fake/->request "https://app.test/x")))))

          (it "->fallback uses a provided response"
              (let [r (fake/->response "down" {:status 200})]
                (should= r (sut/->fallback {:fallback r} (fake/->request "https://app.test/x")))))

          (it "->fallback calls a fallback fn with the request"
              (let [req (fake/->request "https://app.test/x")]
                (should= req (sut/->fallback {:fallback (fn [rq] rq)} req))))

          (it "cache-response! stores cacheable responses and returns the original"
              (let [ctx (fake/->ctx)
                    req (fake/->request "https://app.test/a")
                    rsp (fake/->response "x")]
                (should= rsp (sut/cache-response! ctx {:cache "c"} req rsp))
                (should-contain "https://app.test/a" (store-keys (open-cache ctx "c")))))

          (it "cache-response! does not store non-cacheable responses"
              (let [ctx (fake/->ctx)
                    req (fake/->request "https://app.test/a" {:method "POST"})
                    rsp (fake/->response "x")]
                (sut/cache-response! ctx {:cache "c"} req rsp)
                (should= [] (store-keys (open-cache ctx "c"))))))

(defn seed-cache! [ctx name request response]
  (resolved-value (.then (.open (:caches ctx) name) (fn [c] (.put c request response)))))

(describe "cache-first"
          (it "returns cached response without fetching"
              (let [ctx (fake/->ctx {:fetch (fake/fake-fetch :reject)})
                    req (fake/->request "https://app.test/a")
                    hit (fake/->response "cached")]
                (seed-cache! ctx "c" req hit)
                (should= hit (resolved-value ((sut/cache-first {:cache "c"}) ctx req)))))

          (it "fetches, caches, and returns on cache miss"
              (let [net (fake/->response "fresh")
                    ctx (fake/->ctx {:fetch (fake/fake-fetch net)})
                    req (fake/->request "https://app.test/a")]
                (should= net (resolved-value ((sut/cache-first {:cache "c"}) ctx req)))
                (should-contain "https://app.test/a" (store-keys (open-cache ctx "c")))))

          (it "returns fallback when miss and network fails"
              (let [ctx (fake/->ctx {:fetch (fake/fake-fetch :reject)})
                    req (fake/->request "https://app.test/a")]
                (should= 503 (.-status (resolved-value ((sut/cache-first {:cache "c"}) ctx req)))))))

(describe "network-first"
          (it "fetches, caches, and returns when online"
              (let [net (fake/->response "fresh")
                    ctx (fake/->ctx {:fetch (fake/fake-fetch net)})
                    req (fake/->request "https://app.test/a")]
                (should= net (resolved-value ((sut/network-first {:cache "c"}) ctx req)))
                (should-contain "https://app.test/a" (store-keys (open-cache ctx "c")))))

          (it "falls back to cache when network fails"
              (let [ctx (fake/->ctx {:fetch (fake/fake-fetch :reject)})
                    req (fake/->request "https://app.test/a")
                    hit (fake/->response "cached")]
                (seed-cache! ctx "c" req hit)
                (should= hit (resolved-value ((sut/network-first {:cache "c"}) ctx req)))))

          (it "returns 503 fallback when network fails and no cache"
              (let [ctx (fake/->ctx {:fetch (fake/fake-fetch :reject)})
                    req (fake/->request "https://app.test/a")]
                (should= 503 (.-status (resolved-value ((sut/network-first {:cache "c"}) ctx req)))))))
