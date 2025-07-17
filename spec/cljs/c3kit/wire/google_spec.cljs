(ns c3kit.wire.google-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-select]]
                   [speclj.core :refer [after-all around focus-it before context describe it redefs-around should-be-nil should-have-invoked should-not-have-invoked should= stub with with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.google :as sut]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.spec-helper :as wire]
            [c3kit.wire.spec-helper :as helper]))

(defn render-oauth-button []
  (helper/render [:f> sut/oauth-button {:oauth :options} [:button#-oauth-button]]))

(declare google-id)

(defn create-google []
  (let [this (js-obj)]
    (letfn [(render-button [node options]
              (doto this
                (ccc/oset "node" node)
                (ccc/oset "options" options)))]
      (ccc/oset this "renderButton" render-button)
      this)))

(describe "Google OAuth"
  (with-stubs)
  (helper/with-root-dom)
  (around [it] (log/capture-logs (it)))

  (with google-id (create-google))
  (before (ccc/oset-in js/window ["google" "accounts" "id"] @google-id))
  (after-all (wjs/o-dissoc! js/window "google"))

  (it "renders body"
    (render-oauth-button)
    (helper/flush)
    (should-select "#-oauth-button"))

  (it "mounts button when doc is ready"
    (render-oauth-button)
    (should= (wire/select "#-oauth-button") (ccc/oget @google-id "node"))
    (should= {"oauth" "options"} (js->clj (ccc/oget @google-id "options"))))

  (it "mounts button when doc is not ready"
    (with-redefs [wjs/doc-ready-state (constantly "interactive")]
      (render-oauth-button)
      (should-be-nil (ccc/oget @google-id "node"))
      (should-be-nil (ccc/oget @google-id "options"))
      (wjs/dispatch-event js/window "load")
      (should= (wire/select "#-oauth-button") (ccc/oget @google-id "node"))
      (should= {"oauth" "options"} (js->clj (ccc/oget @google-id "options")))))

  (it "account id is missing"
    (wjs/o-dissoc-in! js/window ["google" "accounts" "id"])
    (render-oauth-button)
    (should= "window.google.accounts.id doesn't exist" (log/captured-logs-str)))
  )
