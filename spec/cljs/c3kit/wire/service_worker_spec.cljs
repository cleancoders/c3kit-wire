(ns c3kit.wire.service-worker-spec
  (:require-macros [speclj.core :refer [before context describe it should should= should-be-nil should-not should-contain with]])
  (:require [c3kit.wire.js :as wjs]
            [c3kit.wire.service-worker-fake :as fake]
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
          (with ctx (fake/->ctx {:scope (fake/->scope {:origin "https://app.test"})}))
          (with get-req (fake/->request "https://app.test/img.png"))
          (with ok-resp (fake/->response "x"))

          (it "true for same-origin ok GET"
              (should= true (sut/cacheable? @ctx @get-req @ok-resp {})))

          (it "false for non-GET"
              (should= false (sut/cacheable? @ctx (fake/->request "https://app.test/x" {:method "POST"}) @ok-resp {})))

          (it "false for non-ok response"
              (should= false (sut/cacheable? @ctx @get-req (fake/->response "x" {:ok false}) {})))

          (it "false for opaque response"
              (should= false (sut/cacheable? @ctx @get-req (fake/->response "x" {:type "opaque"}) {})))

          (it "false for opaqueredirect response"
              (should= false (sut/cacheable? @ctx @get-req (fake/->response "x" {:type "opaqueredirect"}) {})))

          (it "false for no-store response"
              (should= false (sut/cacheable? @ctx @get-req (fake/->response "x" {:headers {"Cache-Control" "no-store"}}) {})))

          (it "false for cross-origin by default, true when allowed"
              (let [x (fake/->request "https://cdn.other/x.png")]
                (should= false (sut/cacheable? @ctx x @ok-resp {}))
                (should= true  (sut/cacheable? @ctx x @ok-resp {:allow-cross-origin true}))))

          (it "false for credentialed by default, true when allowed"
              (let [c (fake/->request "https://app.test/x" {:credentials "include"})
                    a (fake/->request "https://app.test/x" {:headers {"Authorization" "Bearer t"}})]
                (should= false (sut/cacheable? @ctx c @ok-resp {}))
                (should= false (sut/cacheable? @ctx a @ok-resp {}))
                (should= true  (sut/cacheable? @ctx c @ok-resp {:cache-credentialed true}))
                (should= true  (sut/cacheable? @ctx a @ok-resp {:cache-credentialed true}))))

          (it "false for Cache-Control: private"
              (should= false (sut/cacheable? @ctx @get-req (fake/->response "x" {:headers {"Cache-Control" "private, max-age=0"}}) {})))

          (it "false for Cache-Control: no-cache"
              (should= false (sut/cacheable? @ctx @get-req (fake/->response "x" {:headers {"Cache-Control" "no-cache"}}) {})))

          (it "false for Vary: Cookie"
              (should= false (sut/cacheable? @ctx @get-req (fake/->response "x" {:headers {"Vary" "Cookie"}}) {})))

          (it "false for Vary: *"
              (should= false (sut/cacheable? @ctx @get-req (fake/->response "x" {:headers {"Vary" "*"}}) {})))

          (it "false when Set-Cookie present"
              (should= false (sut/cacheable? @ctx @get-req (fake/->response "x" {:headers {"Set-Cookie" "sid=abc"}}) {}))))

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
                (should= [] (store-keys (open-cache ctx "c")))))

          (it "cache-response! returns original response even when put rejects"
              (let [ctx (fake/->ctx {:caches (fake/->caches {:reject-put #{"c"}})})
                    req (fake/->request "https://app.test/a")
                    rsp (fake/->response "x")]
                (should= rsp (sut/cache-response! ctx {:cache "c"} req rsp)))))

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
                (should= 503 (.-status (resolved-value ((sut/cache-first {:cache "c"}) ctx req))))))

          (it "uses custom :fallback fn on miss and network fail"
              (let [ctx (fake/->ctx {:fetch (fake/fake-fetch :reject)})
                    req (fake/->request "https://app.test/a")]
                (should= "custom"
                         (.-body (resolved-value ((sut/cache-first {:cache "c" :fallback (fn [_] (fake/->response "custom"))}) ctx req)))))))

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
                (should= 503 (.-status (resolved-value ((sut/network-first {:cache "c"}) ctx req))))))

          (it "uses custom :fallback fn when network fails and no cache"
              (let [ctx (fake/->ctx {:fetch (fake/fake-fetch :reject)})
                    req (fake/->request "https://app.test/a")]
                (should= "custom"
                         (.-body (resolved-value ((sut/network-first {:cache "c" :fallback (fn [_] (fake/->response "custom"))}) ctx req)))))))

(describe "stale-while-revalidate"
          (it "returns cached immediately and refreshes the cache in the background"
              (let [net (fake/->response "fresh")
                    ctx (fake/->ctx {:fetch (fake/fake-fetch net)})
                    req (fake/->request "https://app.test/a")
                    hit (fake/->response "stale")]
                (seed-cache! ctx "c" req hit)
                (should= hit (resolved-value ((sut/stale-while-revalidate {:cache "c"}) ctx req)))
                ;; background revalidation overwrote the cache entry with the network response
                (should= "fresh" (.-body (resolved-value (.then (.open (:caches ctx) "c") (fn [c] (.match c req))))))))

          (it "awaits network on cache miss"
              (let [net (fake/->response "fresh")
                    ctx (fake/->ctx {:fetch (fake/fake-fetch net)})
                    req (fake/->request "https://app.test/a")]
                (should= net (resolved-value ((sut/stale-while-revalidate {:cache "c"}) ctx req)))))

          (it "returns 503 fallback on cache miss when fetch also rejects"
              (let [ctx (fake/->ctx {:fetch (fake/fake-fetch :reject)})
                    req (fake/->request "https://app.test/a")]
                (should= 503 (.-status (resolved-value ((sut/stale-while-revalidate {:cache "c"}) ctx req)))))))

(describe "network-only"
          (it "returns the network response and caches nothing"
              (let [net (fake/->response "fresh")
                    ctx (fake/->ctx {:fetch (fake/fake-fetch net)})
                    req (fake/->request "https://app.test/a")]
                (should= net (resolved-value ((sut/network-only {}) ctx req)))))

          (it "returns fallback when offline"
              (let [ctx (fake/->ctx {:fetch (fake/fake-fetch :reject)})
                    req (fake/->request "https://app.test/a")]
                (should= 503 (.-status (resolved-value ((sut/network-only {}) ctx req))))))

          (it "writes nothing to any cache"
              (let [net (fake/->response "fresh")
                    ctx (fake/->ctx {:fetch (fake/fake-fetch net)})
                    req (fake/->request "https://app.test/a")]
                (resolved-value ((sut/network-only {}) ctx req))
                (should= [] (store-keys (open-cache ctx "x"))))))

(describe "cache-only"
          (it "returns cached response"
              (let [ctx (fake/->ctx)
                    req (fake/->request "https://app.test/a")
                    hit (fake/->response "cached")]
                (seed-cache! ctx "c" req hit)
                (should= hit (resolved-value ((sut/cache-only {:cache "c"}) ctx req)))))

          (it "returns fallback on miss without hitting network"
              (let [ctx (fake/->ctx {:fetch (fake/fake-fetch :reject)})
                    req (fake/->request "https://app.test/a")]
                (should= 503 (.-status (resolved-value ((sut/cache-only {:cache "c"}) ctx req)))))))

(describe "route registry"
          (before (sut/reset-config!))

          (it "matches by predicate fn, first match wins"
              (let [a (fn [_ _] :a) b (fn [_ _] :b)]
                (sut/register-route! (fn [req] (re-find #"/a" (.-url req))) a)
                (sut/register-route! (fn [_] true) b)
                (should= a (sut/match-route (fake/->request "https://host.test/a")))
                (should= b (sut/match-route (fake/->request "https://host.test/z")))))

          (it "matches by regexp against url"
              (sut/register-route! #"/api/" (fn [_ _] :api))
              (should-not (nil? (sut/match-route (fake/->request "https://app.test/api/x"))))
              (should-be-nil (sut/match-route (fake/->request "https://app.test/page"))))

          (it "matches by exact pathname string"
              (sut/register-route! "/exact" (fn [_ _] :exact))
              (should-not (nil? (sut/match-route (fake/->request "https://app.test/exact"))))
              (should-be-nil (sut/match-route (fake/->request "https://app.test/exact/more"))))

          (it "returns nil when nothing matches"
              (should-be-nil (sut/match-route (fake/->request "https://app.test/x"))))

          (it "precache! and strategy caches populate known-cache-names"
              (sut/precache! ["/"] "shell")
              (sut/register-route! #"/img/" (sut/cache-first {:cache "images"}))
              (should-contain "shell" (sut/known-cache-names))
              (should-contain "images" (sut/known-cache-names))))

(defn ->event [] (let [a (atom {})]
                   (js-obj "waitUntil"   (fn [p] (swap! a assoc :wait p) p)
                           "respondWith" (fn [r] (swap! a assoc :respond r) r)
                           "__a" a)))
(defn event-wait [e] (:wait @(unchecked-get e "__a")))
(defn event-respond [e] (:respond @(unchecked-get e "__a")))

(describe "handle-fetch"
          (before (sut/reset-config!))

          (it "routes GET to the matching strategy"
              (sut/register-route! (fn [_] true) (fn [_ _] (fake/->resolved (fake/->response "routed"))))
              (should= "routed" (.-body (resolved-value (sut/handle-fetch (fake/->ctx) (fake/->request "https://app.test/a"))))))

          (it "uses the default handler when no route matches"
              (sut/set-default! (fn [_ _] (fake/->resolved (fake/->response "default"))))
              (should= "default" (.-body (resolved-value (sut/handle-fetch (fake/->ctx) (fake/->request "https://app.test/a"))))))

          (it "passes non-GET straight to the network"
              (let [net (fake/->response "net")
                    ctx (fake/->ctx {:fetch (fake/fake-fetch net)})]
                (should= net (resolved-value (sut/handle-fetch ctx (fake/->request "https://app.test/a" {:method "POST"}))))))

          (it "resolves to a 503 fallback when strategy returns nil"
              (sut/register-route! (fn [_] true) (fn [_ _] (fake/->resolved nil)))
              (should= 503 (.-status (resolved-value (sut/handle-fetch (fake/->ctx) (fake/->request "https://app.test/a"))))))

          (it "passes GET with no route and no default straight to the network"
              (let [net (fake/->response "passthrough")
                    ctx (fake/->ctx {:fetch (fake/fake-fetch net)})]
                (should= net (resolved-value (sut/handle-fetch ctx (fake/->request "https://app.test/a")))))))

(describe "install"
          (before (sut/reset-config!))

          (it "precaches configured urls and waits on it"
              (let [ctx (fake/->ctx) e (->event)]
                (sut/precache! ["https://app.test/shell"] "shell")
                (sut/install ctx e)
                (resolved-value (event-wait e))
                (should-contain "https://app.test/shell" (store-keys (open-cache ctx "shell")))))

          (it "does not call waitUntil when no precache is configured"
              (let [ctx (fake/->ctx) e (->event)]
                (sut/install ctx e)
                (should-be-nil (event-wait e)))))

(describe "activate"
          (before (sut/reset-config!))

          (it "deletes caches not in the known set and keeps known ones"
              (let [ctx (fake/->ctx) e (->event)]
                (sut/precache! ["/"] "keep")
                (resolved-value (.open (:caches ctx) "keep"))
                (resolved-value (.open (:caches ctx) "stale"))
                (sut/activate ctx e)
                (resolved-value (event-wait e))
                (let [names (js->clj (resolved-value (.keys (:caches ctx))))]
                  (should-contain "keep" names)
                  (should-not (some #{"stale"} names)))))

          (it "deletes two stale caches and keeps known ones"
              (let [ctx (fake/->ctx) e (->event)]
                (sut/precache! ["/"] "keep")
                (resolved-value (.open (:caches ctx) "keep"))
                (resolved-value (.open (:caches ctx) "stale1"))
                (resolved-value (.open (:caches ctx) "stale2"))
                (sut/activate ctx e)
                (resolved-value (event-wait e))
                (let [names (js->clj (resolved-value (.keys (:caches ctx))))]
                  (should-contain "keep" names)
                  (should-not (some #{"stale1"} names))
                  (should-not (some #{"stale2"} names)))))

          (it "claims clients even when a stale cache delete rejects"
              (let [ctx   (fake/->ctx {:caches (fake/->caches {:reject-delete #{"stale"}})})
                    e     (->event)
                    scope (:scope ctx)]
                (sut/precache! ["/"] "keep")
                (resolved-value (.open (:caches ctx) "keep"))
                (resolved-value (.open (:caches ctx) "stale"))
                (sut/activate ctx e)
                (resolved-value (event-wait e))
                (should= true @(unchecked-get scope "__claimed_atom")))))

(describe "start!"
          (it "registers install, activate, and fetch listeners on the scope"
              (let [events (atom [])
                    scope  (fake/->scope)]
                (with-redefs [wjs/add-listener (fn [_ ev _] (swap! events conj ev))]
                  (sut/start! (fake/->ctx {:scope scope}))
                  (should-contain "install" @events)
                  (should-contain "activate" @events)
                  (should-contain "fetch" @events)))))
