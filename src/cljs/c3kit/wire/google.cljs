(ns c3kit.wire.google
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]
            [reagent.core :as r]
            ["react" :as react]))

;; https://developers.google.com/identity/gsi/web/reference/html-reference#element_with_class_g_id_signin

(defn mount-oauth-button [node options]
  (if-let [google-id (ccc/oget-in js/window ["google" "accounts" "id"])]
    (js-invoke google-id "renderButton" node (clj->js options))
    (log/warn "window.google.accounts.id doesn't exist")))

(defn- on-button-mount [options ref]
  (let [node ref]
    (if (wjs/doc-ready?)
      (mount-oauth-button node options)
      (wjs/add-listener js/window "load" #(mount-oauth-button node options)))))

(defn oauth-button
  "Renders the Google OAuth Button. Function component. Must be rendered with :f> or with Reagent compiler set to
  {:function-components true}"
  [options body]
  (r/with-let [ref (atom nil)]
    (react/useEffect (fn [] (when @ref (on-button-mount options @ref)) js/undefined))
    (conj body {:ref #(reset! ref %)})))

; TODO [ARR] - we need a better way to add the ref here that accounts for option passed in in the body