(ns c3kit.wire.ajax
  (:require [c3kit.wire.core.ajax :as core]
            [c3kit.wire.flash]
            [reagent.core :as reagent]))

(defonce -state (core/make-state reagent/atom))

(def active-ajax-requests (:active-requests -state))
(defn activity? [] (not= 0 @active-ajax-requests))

(def server-down?                 core/server-down?)
(def handle-unexpected-response   core/handle-unexpected-response)
(def prep-csrf                    core/prep-csrf)
(def params-key                   core/params-key)
(def pass-through-keys            core/pass-through-keys)
(def request-map                  core/request-map)
(def build-ajax-call              core/build-ajax-call)

(defn handle-server-down [ajax-call]
  (core/handle-server-down -state ajax-call))

(defn handle-unsuccessful-response [response ajax-call]
  (core/handle-unsuccessful-response -state response ajax-call))

(defn triage-response [response ajax-call]
  (core/triage-response -state response ajax-call))

(defn -do-ajax-request [ajax-call]
  (core/-do-ajax-request -state ajax-call))

(defn get!     [url params handler & opts] (apply core/get!     -state url params handler opts))
(defn post!    [url params handler & opts] (apply core/post!    -state url params handler opts))
(defn request! [m url params handler & opts] (apply core/request! -state m url params handler opts))
