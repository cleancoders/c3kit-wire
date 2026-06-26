(ns c3kit.wire.service-worker
  "Secure-by-default offline caching for service workers. All handlers take an
   injected ctx {:caches :fetch :scope} (DIP) and only chain .then/.catch on the
   thenables those injected objects return — never constructing js/Promise — so the
   same code runs async in production and synchronously under test."
  (:require [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]
            [clojure.string :as str]))

;; ---- security policy -------------------------------------------------------

(defn- scope-origin [ctx] (.. (:scope ctx) -location -origin))
(defn- request-origin [request] (.-origin (js/URL. (.-url request))))
(defn- same-origin? [ctx request] (= (scope-origin ctx) (request-origin request)))

(defn- opaque? [response] (boolean (#{"opaque" "opaqueredirect"} (.-type response))))

(defn- cache-control-tokens [response]
  (some-> (.. response -headers) (.get "Cache-Control") str/lower-case (str/split #"[,\s]+")))

(defn- cache-control-blocks? [response]
  (boolean (some #{"no-store" "no-cache" "private"} (cache-control-tokens response))))

(defn- vary-sensitive? [response]
  (boolean (some-> (.. response -headers) (.get "Vary") str/lower-case
                   (str/split #"[,\s]+")
                   (as-> tokens (some #{"*" "cookie" "authorization"} tokens)))))

(defn- set-cookie? [response]
  (boolean (some-> (.. response -headers) (.get "Set-Cookie"))))

(defn- credentialed? [request]
  (or (= "include" (.-credentials request))
      (boolean (some-> (.-headers request) (.get "Authorization")))))

(defn cacheable?
  "Should this response be written to the cache? Secure by default.

   Hard blocks (not overridable by opts):
   - Cache-Control: no-store, no-cache, or private
   - Vary: * or Cookie or Authorization (per-user content)
   - Set-Cookie present (response sets session state)

   Threat-model note: detects credentials via Authorization header or
   credentials: \"include\", and blocks Vary/Set-Cookie/private/no-cache
   responses. CANNOT detect same-origin cookie auth — apps must route
   private cookie-authenticated endpoints to network-only (or omit caching)."
  [ctx request response opts]
  (and (= "GET" (.-method request))
       (boolean (.-ok response))
       (not (opaque? response))
       (not (cache-control-blocks? response))
       (not (vary-sensitive? response))
       (not (set-cookie? response))
       (or (:allow-cross-origin opts) (same-origin? ctx request))
       (or (:cache-credentialed opts) (not (credentialed? request)))))

;; ---- cache helpers ---------------------------------------------------------

(defn- open-cache [ctx name] (.open (:caches ctx) name))

(defn- cache-match [ctx name request]
  (.then (open-cache ctx name) (fn [cache] (.match cache request))))

(defn- cache-put [ctx name request response]
  (.then (open-cache ctx name) (fn [cache] (.put cache request response))))

(defn ->fallback
  "Return a fallback Response: calls :fallback fn, returns :fallback value, or synthesizes a 503."
  [opts request]
  (let [fb (:fallback opts)]
    (cond
      (fn? fb)   (fb request)
      (some? fb) fb
      :else      (js/Response. nil #js {:status 503 :statusText "Service Unavailable"}))))

(defn cache-response!
  "Clone + cache response when the shared security policy permits. Returns response.
   The cache write is fire-and-forget; any put failure (e.g. QuotaExceededError) is
   swallowed after logging so the calling strategy is never rejected."
  [ctx opts request response]
  (when (cacheable? ctx request response opts)
    (.catch (cache-put ctx (:cache opts) request (.clone response))
            (fn [err] (log/warn "[ServiceWorker] cache put failed:" err))))
  response)

;; ---- caches known to the library (for activate purge) ----------------------

(defonce ^:private known-caches (atom #{}))
(defn- register-cache! [name] (when name (swap! known-caches conj name)) name)

;; ---- strategies (LSP: each returns (fn [ctx request] -> thenable<Response>)) ----

(defn- invoke-fetch [ctx request] ((:fetch ctx) request))

(defn cache-first
  "Strategy: serve from cache; on miss fetch, cache, and return; fallback on any error."
  [opts]
  (register-cache! (:cache opts))
  (fn [ctx request]
    (-> (cache-match ctx (:cache opts) request)
        (.then (fn [cached]
                 (or cached
                     (.then (invoke-fetch ctx request)
                            (fn [response] (cache-response! ctx opts request response))))))
        (.catch (fn [err] (log/warn "[ServiceWorker] cache-first error:" err) (->fallback opts request))))))

(defn network-first
  "Strategy: fetch from network and cache; on network error serve from cache; if the cache also misses, return the fallback."
  [opts]
  (register-cache! (:cache opts))
  (fn [ctx request]
    (-> (invoke-fetch ctx request)
        (.then (fn [response] (cache-response! ctx opts request response)))
        (.catch (fn [err]
                  (log/warn "[ServiceWorker] network-first fetch error:" err)
                  (.then (cache-match ctx (:cache opts) request)
                         (fn [cached] (or cached (->fallback opts request)))))))))

(defn stale-while-revalidate
  "Strategy: serve cached immediately; refresh cache from network in background; await network on miss.
   Note: the background revalidation is fire-and-forget — it is not wrapped in event.waitUntil (the
   strategy has no access to the fetch event), so a browser may terminate the worker before the refresh
   write completes. The cache still refreshes on a normal page that stays alive; the next request gets the
   prior cached value and triggers a fresh revalidation."
  [opts]
  (register-cache! (:cache opts))
  (fn [ctx request]
    (.then (cache-match ctx (:cache opts) request)
           (fn [cached]
             (let [network (-> (invoke-fetch ctx request)
                               (.then (fn [response] (cache-response! ctx opts request response)))
                               (.catch (fn [err] (log/warn "[ServiceWorker] stale-while-revalidate error:" err) (->fallback opts request))))]
               (or cached network))))))

(defn network-only
  "Strategy: always fetch from network; no caching; fallback on network error."
  [opts]
  (fn [ctx request]
    (.catch (invoke-fetch ctx request) (fn [err] (log/warn "[ServiceWorker] network-only error:" err) (->fallback opts request)))))

(defn cache-only
  "Strategy: serve from cache only; no network; fallback on cache miss."
  [opts]
  (register-cache! (:cache opts))
  (fn [ctx request]
    (.then (cache-match ctx (:cache opts) request)
           (fn [cached] (or cached (->fallback opts request))))))

;; ---- route registry --------------------------------------------------------

(defonce ^:private routes (atom []))
(defonce ^:private default-handler (atom nil))
(defonce ^:private precache-config (atom nil))

(defn reset-config!
  "Reset all route, default-handler, precache, and known-cache state to empty."
  []
  (reset! routes [])
  (reset! default-handler nil)
  (reset! precache-config nil)
  (reset! known-caches #{}))

(defn- ->matcher [m]
  (cond
    (fn? m)      m
    (regexp? m)  (fn [request] (boolean (re-find m (.-url request))))
    (string? m)  (fn [request] (= m (.-pathname (js/URL. (.-url request)))))
    :else        (throw (ex-info "invalid route matcher" {:matcher m}))))

(defn register-route!
  "Register a strategy for requests matching matcher (fn, regexp, or exact-pathname string)."
  [matcher strategy]
  (swap! routes conj {:match (->matcher matcher) :handler strategy})
  nil)

(defn set-default!
  "Set the fallback strategy used when no registered route matches a GET request."
  [strategy] (reset! default-handler strategy) nil)

(defn match-route
  "Return the first registered strategy whose matcher accepts request, or nil."
  [request]
  (some (fn [{:keys [match handler]}] (when (match request) handler)) @routes))

(defn precache!
  "Configure urls to be fetched and stored in cache-name during the install event."
  [urls cache-name]
  (register-cache! cache-name)
  (reset! precache-config {:cache cache-name :urls (vec urls)})
  nil)

(defn known-cache-names
  "Return the set of cache names registered by strategies and precache! (used during activate purge)."
  [] @known-caches)

;; ---- lifecycle -------------------------------------------------------------

(defn- network-passthrough [ctx request] (invoke-fetch ctx request))

(defn handle-fetch
  "Dispatch a fetch event's request to the matching route strategy, default handler, or network passthrough.
   Guards the resolved value: if a strategy resolves to nil/undefined (which would cause
   respondWith(undefined) to crash the page), substitutes a 503 fallback response."
  [ctx request]
  (let [thenable (if (= "GET" (.-method request))
                   (if-let [handler (or (match-route request) @default-handler)]
                     (handler ctx request)
                     (network-passthrough ctx request))
                   (network-passthrough ctx request))]
    (.then thenable (fn [resp]
                      (if (some? resp)
                        resp
                        (->fallback {} request))))))

(defn install
  "Handle the SW install event: skipWaiting and precache configured urls if any."
  [ctx event]
  (log/info "[ServiceWorker] install")
  (.skipWaiting (:scope ctx))
  (when-let [{:keys [cache urls]} @precache-config]
    (.waitUntil event
                (.then (open-cache ctx cache) (fn [c] (.addAll c (clj->js urls)))))))

(defn activate
  "Handle the SW activate event: delete stale caches not in known-cache-names, then claim clients.
   Each cache delete is best-effort: a rejected delete (e.g. cache locked/busy) is logged and
   swallowed so that clients.claim is always reached."
  [ctx event]
  (log/info "[ServiceWorker] activate")
  (let [keep?  (known-cache-names)
        caches (:caches ctx)
        names* (.keys caches)]
    (.waitUntil event
                (-> names*
                    (.then (fn [names]
                             (reduce (fn [p name]
                                       (if (keep? name)
                                         p
                                         (.then p (fn [_]
                                                    (.catch (.delete caches name)
                                                            (fn [err]
                                                              (log/warn "[ServiceWorker] cache delete failed for" name ":" err)))))))
                                     names*
                                     (js->clj names))))
                    (.then (fn [_] (.. (:scope ctx) -clients (claim))))))))

(defn- fetch-listener [ctx event]
  (.respondWith event (handle-fetch ctx (.-request event))))

(defn ctx-from-globals
  "Build a production ctx map from ServiceWorkerGlobalScope globals (js/caches, js/fetch, js/self)."
  []
  {:caches js/caches
   :fetch  (fn [request] (js/fetch request))
   :scope  js/self})

(defn start!
  "Wire install, activate, and fetch listeners onto the SW scope. Calls ctx-from-globals when ctx omitted."
  ([] (start! (ctx-from-globals)))
  ([ctx]
   (wjs/add-listener (:scope ctx) "install"  (fn [e] (install ctx e)))
   (wjs/add-listener (:scope ctx) "activate" (fn [e] (activate ctx e)))
   (wjs/add-listener (:scope ctx) "fetch"    (fn [e] (fetch-listener ctx e)))
   nil))

