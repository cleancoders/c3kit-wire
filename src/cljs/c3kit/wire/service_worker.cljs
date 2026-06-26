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
