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

(defn- no-store? [response]
  (boolean (some-> (.. response -headers) (.get "Cache-Control") (str/includes? "no-store"))))

(defn- credentialed? [request]
  (or (= "include" (.-credentials request))
      (boolean (some-> (.-headers request) (.get "Authorization")))))

(defn cacheable?
  "Should this response be written to the cache? Secure by default."
  [ctx request response opts]
  (and (= "GET" (.-method request))
       (boolean (.-ok response))
       (not (opaque? response))
       (not (no-store? response))
       (or (:allow-cross-origin opts) (same-origin? ctx request))
       (or (:cache-credentialed opts) (not (credentialed? request)))))

;; ---- cache helpers ---------------------------------------------------------

(defn- open-cache [ctx name] (.open (:caches ctx) name))

(defn- cache-match [ctx name request]
  (.then (open-cache ctx name) (fn [cache] (.match cache request))))

(defn- cache-put [ctx name request response]
  (.then (open-cache ctx name) (fn [cache] (.put cache request response))))

(defn ->fallback [opts request]
  (let [fb (:fallback opts)]
    (cond
      (fn? fb)   (fb request)
      (some? fb) fb
      :else      (js/Response. nil #js {:status 503 :statusText "Service Unavailable"}))))

(defn cache-response!
  "Clone + cache response when the shared security policy permits. Returns response."
  [ctx opts request response]
  (when (cacheable? ctx request response opts)
    (cache-put ctx (:cache opts) request (.clone response)))
  response)

;; ---- caches known to the library (for activate purge) ----------------------

(defonce ^:private known-caches (atom #{}))
(defn- register-cache! [name] (when name (swap! known-caches conj name)) name)

;; ---- strategies (LSP: each returns (fn [ctx request] -> thenable<Response>)) ----

(defn- invoke-fetch [ctx request] ((:fetch ctx) request))

(defn cache-first [opts]
  (register-cache! (:cache opts))
  (fn [ctx request]
    (-> (cache-match ctx (:cache opts) request)
        (.then (fn [cached]
                 (or cached
                     (.then (invoke-fetch ctx request)
                            (fn [response] (cache-response! ctx opts request response))))))
        (.catch (fn [_] (->fallback opts request))))))

(defn network-first [opts]
  (register-cache! (:cache opts))
  (fn [ctx request]
    (-> (invoke-fetch ctx request)
        (.then (fn [response] (cache-response! ctx opts request response)))
        (.catch (fn [_]
                  (.then (cache-match ctx (:cache opts) request)
                         (fn [cached] (or cached (->fallback opts request)))))))))

(defn stale-while-revalidate [opts]
  (register-cache! (:cache opts))
  (fn [ctx request]
    (.then (cache-match ctx (:cache opts) request)
           (fn [cached]
             (let [network (-> (invoke-fetch ctx request)
                               (.then (fn [response] (cache-response! ctx opts request response)))
                               (.catch (fn [_] (->fallback opts request))))]
               (or cached network))))))

(defn network-only [opts]
  (fn [ctx request]
    (.catch (invoke-fetch ctx request) (fn [_] (->fallback opts request)))))

(defn cache-only [opts]
  (register-cache! (:cache opts))
  (fn [ctx request]
    (.then (cache-match ctx (:cache opts) request)
           (fn [cached] (or cached (->fallback opts request))))))

;; ---- route registry --------------------------------------------------------

(defonce ^:private routes (atom []))
(defonce ^:private default-handler (atom nil))
(defonce ^:private precache-config (atom nil))

(defn reset-config! []
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

(defn register-route! [matcher strategy]
  (swap! routes conj {:match (->matcher matcher) :handler strategy})
  nil)

(defn set-default! [strategy] (reset! default-handler strategy) nil)

(defn match-route [request]
  (some (fn [{:keys [match handler]}] (when (match request) handler)) @routes))

(defn precache! [urls cache-name]
  (register-cache! cache-name)
  (reset! precache-config {:cache cache-name :urls (vec urls)})
  nil)

(defn known-cache-names [] @known-caches)

;; ---- lifecycle -------------------------------------------------------------

(defn- network-passthrough [ctx request] (invoke-fetch ctx request))

(defn handle-fetch [ctx request]
  (if (= "GET" (.-method request))
    (if-let [handler (or (match-route request) @default-handler)]
      (handler ctx request)
      (network-passthrough ctx request))
    (network-passthrough ctx request)))

(defn install [ctx event]
  (log/info "[ServiceWorker] install")
  (.skipWaiting (:scope ctx))
  (when-let [{:keys [cache urls]} @precache-config]
    (.waitUntil event
                (.then (open-cache ctx cache) (fn [c] (.addAll c (clj->js urls)))))))

(defn activate [ctx event]
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
                                         (.then p (fn [_] (.delete caches name)))))
                                     names*
                                     (js->clj names))))
                    (.then (fn [_] (.. (:scope ctx) -clients (claim))))))))

(defn- fetch-listener [ctx event]
  (.respondWith event (handle-fetch ctx (.-request event))))

(defn ctx-from-globals []
  {:caches js/caches
   :fetch  (fn [request] (js/fetch request))
   :scope  js/self})

(defn start!
  ([] (start! (ctx-from-globals)))
  ([ctx]
   (wjs/add-listener (:scope ctx) "install"  (fn [e] (install ctx e)))
   (wjs/add-listener (:scope ctx) "activate" (fn [e] (activate ctx e)))
   (wjs/add-listener (:scope ctx) "fetch"    (fn [e] (fetch-listener ctx e)))
   nil))

