(ns c3kit.wire.api
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.apic :as apic]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.flashc :as flashc]
            [c3kit.wire.js :as cc]))

(def config (atom {
                   :version                       "undefined"
                   :redirect-fn                   cc/redirect!
                   :ajax-prep-fn                  nil       ;; (fn [ajax-call] modified-ajax-call)
                   :ajax-on-unsuccessful-response nil       ;; (fn [response ajax-call] ...)
                   :ws-csrf-token                 nil
                   :ws-on-reconnected             nil
                   :ws-uri-path                   "/user/websocket"
                   }))

(defn configure! [& options] (swap! config merge (ccc/->options options)))

(defn- handle-payload [handler payload]
  (try
    (handler payload)
    (catch :default e
      (log/error "AJAX handler error")
      (log/error e)
      (flash/add-error! "Oh no!  I choked on some data.  Doh!"))))

(defn- redirect [uri] (some-> @config :redirect-fn (ccc/invoke uri)))
(def refresh-link [:a {:href "#" :on-click (comp cc/redirect! cc/page-href)} "refresh"])
(def server-down-flash (flashc/create :warn "Server Maintenance - please wait a moment as we try to reconnect." true))
(def new-version-flash (flashc/create :warn (list "There is a newer version of this app.  Please " refresh-link ".") true))
(def forbidden-flash (flashc/create :warn (list "403 - Forbidden.  Please " refresh-link ".") true))

(defn new-version! [version]
  (log/warn "new version: " version ", was: " (:version @config))
  (flash/add! new-version-flash))

;; Options:    - extensible
;;  :after-all - a no-arg fn that is always invoked at the end of the entire call process
;;  :no-redirect - when truthy, redirect is ignored
;;  :on-fail - a (fn [payload] ...) that will be invoked when the response status is :fail
;;  :on-error - a (fn [payload] ...) that will be invoked when the response status is :error

(defn handle-api-response [raw-response {:keys [handler options]}]
  (flash/remove! server-down-flash)
  (log/trace "raw response: " raw-response)
  (let [{:keys [status flash payload version uri]} (apic/conform-response raw-response)]
    (when (seq flash)
      (run! flash/add! flash))
    (when (and version (not= version (:version @config)))
      (new-version! version))
    (when (= :ok status)
      (handle-payload handler payload))
    (when (and (= :redirect status) (not (:no-redirect options)))
      (redirect uri))
    (when (= :fail status)
      (some-> options :on-fail (ccc/invoke payload)))
    (when (= :error status)
      (some-> options :on-error (ccc/invoke payload))))
  (some-> options :after-all ccc/invoke))
