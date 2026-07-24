(ns c3kit.wire.service-worker.fake
  "In-memory test doubles for service-worker specs. Shipped in src so apps that
   build their own SW config can reuse them. Includes a synchronous promise double
   so SW handlers (which only chain .then/.catch on injected thenables) resolve
   synchronously under test.")

(declare ->resolved)

(defn ->rejected [err]
  (js-obj
    "then"  (fn [_] (->rejected err))
    "catch" (fn [f] (->resolved (f err)))))

(defn ->resolved [v]
  (if (and v (fn? (unchecked-get v "then")))
    v
    (js-obj
      "then"  (fn [f] (try (->resolved (f v)) (catch :default e (->rejected e))))
      "catch" (fn [_] (->resolved v)))))

(defn ->headers [m]
  (let [m (or m {})]
    (js-obj "get" (fn [k] (get m k)))))

(defn ->request [url & [{:keys [method headers credentials] :or {method "GET"}}]]
  (js-obj "url" url "method" method "credentials" credentials "headers" (->headers headers)))

(defn ->response [body & [opts]]
  (let [{:keys [ok type status headers] :or {ok true type "basic" status 200}} opts]
    (js-obj "ok" ok "type" type "status" status "body" body
            "headers" (->headers headers)
            "clone" (fn [] (->response body opts)))))

(defn- req-url [r] (if (string? r) r (unchecked-get r "url")))

(defn ->cache
  "Creates a fake Cache. Options:
   :reject-put? - when true, .put returns a rejected thenable (simulates QuotaExceededError)"
  [& [{:keys [reject-put?]}]]
  (let [store (atom {})]
    (js-obj
      "match"  (fn [request] (->resolved (get @store (req-url request))))
      "put"    (fn [request response]
                 (if reject-put?
                   (->rejected (js/Error. "QuotaExceededError"))
                   (do (swap! store assoc (req-url request) response) (->resolved nil))))
      "addAll" (fn [urls] (doseq [u (js->clj urls)] (swap! store assoc u :precached)) (->resolved nil))
      "keys"   (fn [] (->resolved (clj->js (vec (keys @store)))))
      "delete" (fn [request]
                 (let [k (req-url request) had (contains? @store k)]
                   (swap! store dissoc k) (->resolved had)))
      "__store" store)))

(defn ->caches
  "Creates a fake CacheStorage. Options:
   :reject-delete - set of cache names whose .delete should return a rejected thenable
   :reject-put    - set of cache names whose cache's .put should return a rejected thenable"
  [& [{:keys [reject-delete reject-put] :or {reject-delete #{} reject-put #{}}}]]
  (let [caches (atom {})]
    (js-obj
      "open"   (fn [name]
                 (when-not (contains? @caches name)
                   (swap! caches assoc name (->cache {:reject-put? (contains? reject-put name)})))
                 (->resolved (get @caches name)))
      "keys"   (fn [] (->resolved (clj->js (vec (keys @caches)))))
      "has"    (fn [name] (->resolved (contains? @caches name)))
      "delete" (fn [name]
                 (if (contains? reject-delete name)
                   (->rejected (js/Error. (str "delete rejected for " name)))
                   (let [had (contains? @caches name)]
                     (swap! caches dissoc name) (->resolved had))))
      "__caches" caches)))

(defn ->scope
  "Creates a fake ServiceWorkerGlobalScope. Options:
   :origin - the origin string (default \"https://app.test\")
   Access __claimed_atom (an atom) to test whether clients.claim was called."
  [& [{:keys [origin] :or {origin "https://app.test"}}]]
  (let [claimed (atom false)]
    (js-obj
      "location"         (js-obj "origin" origin)
      "skipWaiting"      (fn [] (->resolved nil))
      "clients"          (js-obj "claim" (fn [] (reset! claimed true) (->resolved nil)))
      "addEventListener" (fn [_ _])
      "__claimed_atom"   claimed)))

(defn fake-fetch [resp-or-fn]
  (fn [request]
    (let [r (if (fn? resp-or-fn) (resp-or-fn request) resp-or-fn)]
      (if (= :reject r) (->rejected (js/Error. "network fail")) (->resolved r)))))

(defn ->ctx [& [{:keys [caches fetch scope]}]]
  {:caches (or caches (->caches))
   :fetch  (or fetch (fake-fetch (->response "ok")))
   :scope  (or scope (->scope))})
