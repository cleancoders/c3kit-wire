(ns c3kit.wire.routes
  (:require [c3kit.apron.util :as util]
            [clojure.string :as str]
            [compojure.core :as compojure :refer [routes]]
            [ring.util.response :as response]))

(def reload? (atom false))

(def default-config {:reload? false})

(defn init! [config]
  "Initialize the namespace with the following options:
  {
  :reload? false    ;; when true, will reload handler vars each time they are requested (for development)
  }"
  (reset! reload? (:reload? config false)))

(defn wrap-prefix
  "Scope a set of handler to a prefix.  Subroutes should match the reset of the uri, not including the prefix.
  Unlike compojure.core/context, wrap-prefix will respond with the not-found handler if none of the sub-routes match.
  TODO - allow bindings in the prefix, e.g. (wrap-prefix handler \"/foo/:id\" not-found-handler)"
  [handler prefix not-found-handler]
  (fn [request]
    (let [path (or (:path-info request) (:uri request))]
      (when (str/starts-with? path prefix)
        (let [request (assoc request :path-info (subs path (count prefix)))]
          (if-let [response (handler request)]
            response
            (not-found-handler request)))))))

(defn -resolve-handler [handler-sym] (util/resolve-var handler-sym))
(def -memoized-resolve-handler (memoize -resolve-handler))

(defn lazy-handle
  "handler-sym is assumed to be a fully qualified var that will be loaded and treated like a Ring handler that takes
  one parameter [request]. Runtime errors will occur for missing handlers."
  [handler-sym request]
  (let [resolver (if @reload? -resolve-handler -memoized-resolve-handler)
        handler (resolver handler-sym)]
    (handler request)))

(defmacro lazy-routes
  "Creates compojure route for each entry where the handler is lazily loaded.  This is useful in development so that
  code changes get reloaded with each request.  Be sure to set reload? to true in development."
  [table]
  `(routes
     ~@(for [[[path method] handler-sym] table]
         (let [method (if (= :any method) nil method)]
           (compojure/compile-route method path 'req `((lazy-handle '~handler-sym ~'req)))))))

(defn redirect-handler
  "Creates a handler that will respond with a redirect to the specified path.  If the path contains bindings, the
  redirect URI will be build using parameters from the request."
  [path]
  (let [segments (str/split path #"/")
        segments (map #(if (str/starts-with? % ":") (keyword (subs % 1)) %) segments)]
    (fn [request]
      (let [params   (:params request)
            segments (map #(if (keyword? %) (get params %) %) segments)
            dest     (str/join "/" segments)]
        (response/redirect dest)))))

(defmacro redirect-routes [table]
  "Creates a handler that will redirect the table of routes.
  e.g. (redirect-routes {[\"/old/path\" :any] \"/new/path\" ...})"
  `(routes
     ~@(for [[[path method] dest] table]
         (let [method (if (= :any method) nil method)]
           (compojure/compile-route method path 'req `((redirect-handler ~dest)))))))
