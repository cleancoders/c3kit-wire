(ns c3kit.wire.google
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]
            [reagent.core :as reagent]))

;; https://developers.google.com/identity/gsi/web/reference/html-reference#element_with_class_g_id_signin

(defn mount-oauth-button [node options]
  (if-let [google-id (ccc/oget-in js/window ["google" "accounts" "id"])]
    (js-invoke google-id "renderButton" node (clj->js options))
    (log/warn "window.google.accounts.id doesn't exist")))

(defn- on-button-mount [options node]
  (if (wjs/doc-ready?)
    (mount-oauth-button node options)
    (wjs/add-listener js/window "load" #(mount-oauth-button node options))))

(defn- with-ref [[tag maybe-options :as body] ref]
  (let [has-options? (map? maybe-options)
        ref-option   (:ref maybe-options)
        ref-option   (cond-> #(reset! ref %)
                             (and has-options? ref-option)
                             (juxt ref-option))
        options      (cond->> {:ref ref-option}
                              has-options?
                              (merge maybe-options))
        body         (if has-options?
                       (drop 2 body)
                       (rest body))]
    (vec (concat [tag options] body))))

(defn oauth-button
  "Renders the Google OAuth Button."
  [options _body]
  (let [node (atom nil)]
    (reagent/create-class
      {:component-did-mount #(on-button-mount options @node)
       :reagent-render      (fn [_options body] (with-ref body node))})))
