(ns c3kit.wire.api
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.schema :as schema]
            [c3kit.wire.apic :as apic]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def default-error-message "Our apologies. An error occurred and we have been notified.")

(def default-config {
                     :ajax-on-ex  'c3kit.wire.ajax/default-ajax-ex-handler ;; (fn [request e])
                     :version     "undefined"
                     :ws-handlers nil
                     })

(def config (atom default-config))
(defn configure! [& options] (swap! config merge (ccc/->options options)))

;(defn load-api-version [js-path app]
;  (let [url (or (io/resource js-path) (throw (ex-info "No JS file. Did you forget to do 'lein cljs'?" {:js-path js-path})))
;        file (io/file (.getFile url))
;        timestamp (str (.lastModified file))]
;    (log/report "API version: " timestamp)
;    (assoc app :api/version timestamp)))

(defn version-from-js-file [js-path]
  (let [url  (or (io/resource js-path) (throw (ex-info "No JS file. Did you forget to build it?" {:js-path js-path})))
        file (io/file (.getFile url))]
    (str (.lastModified file))))

(defn version [] (:version @config))

(defn validation-errors-response [error-map]
  (-> (apic/ok {:errors error-map})
      (apic/flash-warn "Validation errors...")))

(defn maybe-validation-errors [entity]
  (some-> entity schema/message-map validation-errors-response))

(defn maybe-entity-errors [entity]
  (some->> entity schema/message-seq (str/join " , ") (apic/fail nil)))

(defn wrap-add-api-version [handler]
  (fn [request]
    (let [{:keys [body] :as response} (handler request)]
      (if (map? body)
        (assoc-in response [:body :version] (version))
        response))))