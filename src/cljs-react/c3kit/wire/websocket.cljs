(ns c3kit.wire.websocket
  (:require [c3kit.wire.core.websocket :as core]
            [c3kit.wire.flash]
            [c3kit.wire.js :as wjs]
            [reagent.core :as reagent]))

(defonce -state (core/make-state reagent/atom))

(def open?         (:open?         -state))
(def reconnection? (:reconnection? -state))
(def pending-calls (:pending-calls -state))

(def handle-remote-response core/handle-remote-response)
(def build-remote-call      core/build-remote-call)
(def make-call!             core/make-call!)
(def connect!               core/connect!)
(def stop!                  core/stop!)

;; 1-arg adapters that thread -state into the 2-arg core multimethod / fn.
(defn push-handler   [message] (core/push-handler   -state message))
(defn message-handler [message] (core/message-handler -state message))

(defn call! [kind params handler & opt-args]
  (apply core/call! -state kind params handler opt-args))

(defn on-open [_] (core/on-open -state nil))

(defn start! [] (core/start! -state reagent/atom))

(defn disconnected-button []
  (let [open? (reagent/atom false)]
    (fn []
      [:div.contextual-menu-anchor
       [:button#-disconnected-button.disconnected.naked {:on-click #(reset! open? true)}
        [:span.fas.fa-exclamation-triangle.animation.error.small-margin-left]]
       (when @open?
         [:div#-disconnected-menu-overlay.contextual-menu {:on-click #(reset! open? false)}
          [:div#-disconnected-menu.card
           [:h5.small-margin-bottom [:span.fas.fa-link] "Connection Broken"]
           [:p.margin-bottom "Your connection with the server has been broken. "
            "We are trying to reconnect.  If that doesn't seem to help, please try reloading this page."]
           [:button.primary {:on-click wjs/page-reload!} "Reload Page"]]])])))

(defn connection-status [] (when-not @open? [disconnected-button]))
