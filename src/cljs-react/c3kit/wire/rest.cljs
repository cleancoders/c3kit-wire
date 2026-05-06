(ns c3kit.wire.rest
  (:require [c3kit.wire.core.rest :as core]
            [c3kit.wire.flash]
            [reagent.core :as reagent]))

(defonce -state (core/make-state reagent/atom))

(def active-reqs (:active-requests -state))
(defn activity? [] (not= 0 @active-reqs))

(def configure!            core/configure!)
(def success?              core/success?)
(def error?                core/error?)
(def bad-req?              core/bad-req?)
(def unauthenticated?      core/unauthenticated?)
(def unauthorized?         core/unauthorized?)
(def not-found?            core/not-found?)
(def server-error?         core/server-error?)
(def payload               core/payload)
(def wrap-success-handler  core/wrap-success-handler)
(def wrap-response-code    core/wrap-response-code)
(def wrap-response-codes   core/wrap-response-codes)
(def wrap-user-handlers    core/wrap-user-handlers)
(def wrap-form-errors      core/wrap-form-errors)
(def with-handlers         core/with-handlers)
(def wrap-handler          core/wrap-handler)

(defn -request! [channel callback] (core/-request! -state channel callback))
(defn request!  [method url request handler options] (core/request! -state method url request handler options))
(defn get!      [url request callback & opts] (apply core/do-get!    -state url request callback opts))
(defn post!     [url request callback & opts] (apply core/do-post!   -state url request callback opts))
(defn put!      [url request callback & opts] (apply core/do-put!    -state url request callback opts))
(defn delete!   [url request callback & opts] (apply core/do-delete! -state url request callback opts))
