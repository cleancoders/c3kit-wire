(ns c3kit.wire.service-worker-register
  "Page-side service-worker registration. register-with takes its secure-context
   flag and serviceWorker container as explicit args (DIP) so it is testable
   without global redefs; register! fills them from globals."
  (:require [c3kit.apron.log :as log]))

(defn- wire-update-callbacks [registration on-update on-active]
  (.addEventListener registration "updatefound"
                     (fn []
                       (when-let [installing (.-installing registration)]
                         (.addEventListener installing "statechange"
                                            (fn []
                                              (case (.-state installing)
                                                "installed" (when on-update (on-update registration))
                                                "activated" (when on-active (on-active registration))
                                                nil)))))))

(defn register-with
  "Register the SW using explicit deps: :container, :secure?, :url, :on-update, :on-active. Testable without globals."
  [{:keys [container secure? url on-update on-active]
    :or   {url "/service-worker.js"}}]
  (cond
    (not secure?)
    (do (log/warn "service worker: insecure context, skipping registration")
        (js/Promise.resolve nil))

    (nil? container)
    (do (log/warn "service worker: unsupported, skipping registration")
        (js/Promise.resolve nil))

    :else
    (-> (.register container url)
        (.then (fn [registration]
                 (wire-update-callbacks registration on-update on-active)
                 (log/info "service worker registered:" url)
                 registration))
        (.catch (fn [err] (log/warn "service worker registration failed:" err) nil)))))

(defn- sw-container [] (when (exists? js/navigator) (.-serviceWorker js/navigator)))
(defn- secure-context? [] (boolean (and (exists? js/self) (.-isSecureContext js/self))))

(defn register!
  "Register the service worker. opts: {:url :on-update :on-active}."
  [opts]
  (register-with (merge {:container (sw-container) :secure? (secure-context?)} opts)))

(defn unregister-with
  "Unregister using explicit container dep. Testable without globals."
  [container]
  (if container
    (-> (.getRegistration container) (.then (fn [reg] (when reg (.unregister reg)))))
    (js/Promise.resolve nil)))

(defn unregister!
  "Unregister the current service worker registration, if any."
  []
  (unregister-with (sw-container)))
