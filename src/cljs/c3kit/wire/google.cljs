(ns c3kit.wire.google
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]))

;; https://developers.google.com/identity/gsi/web/reference/html-reference#element_with_class_g_id_signin

(defn mount-oauth-button [node options]
  (if-let [google-id (ccc/oget-in js/window ["google" "accounts" "id"])]
    (js-invoke google-id "renderButton" node (clj->js options))
    (log/warn "window.google.accounts.id doesn't exist")))

(defn- on-button-mount [options node]
  (if (wjs/doc-ready?)
    (mount-oauth-button node options)
    (wjs/add-listener js/window "load" #(mount-oauth-button node options) :once true)))

(defn- with-ref [[tag maybe-options :as body] ref-fn]
  (let [has-options? (map? maybe-options)
        user-ref     (:ref maybe-options)
        ref-option   (if (and has-options? user-ref)
                       (fn [node] (ref-fn node) (user-ref node))
                       ref-fn)
        options      (cond->> {:ref ref-option}
                              has-options?
                              (merge maybe-options))
        body         (if has-options?
                       (drop 2 body)
                       (rest body))]
    (vec (concat [tag options] body))))

(defn oauth-button
  "Renders the Google OAuth Button."
  [options body]
  (with-ref body (fn [node]
                   (when node
                     (on-button-mount options node)))))
