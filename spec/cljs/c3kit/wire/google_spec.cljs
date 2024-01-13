(ns c3kit.wire.google-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-select]]
                   [speclj.core :refer [after-all around before context describe it redefs-around should-have-invoked should-not-have-invoked should= stub with-stubs]])
  (:require [c3kit.apron.log :as log]
            [c3kit.wire.google :as sut]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.spec-helper :as wire]
            [c3kit.wire.spec-helper :as helper]
            [speclj.stub :as stub]))

(defn render-oauth-button []
  (helper/render [sut/oauth-button {:oauth :options}
                  [:button#-oauth-button]]))

(describe "Google OAuth"
  (with-stubs)
  (helper/with-root-dom)
  (redefs-around [sut/render-button (stub :render-button)
                  wjs/add-listener  (stub :add-listener)
                  wjs/doc-ready?    (constantly true)])

  (before (wjs/o-assoc-in! js/window ["google" "accounts" "id"] "goog-id"))
  (after-all (wjs/o-dissoc! js/window "google"))

  (around [it] (log/capture-logs (it)))

  (it "renders body"
    (render-oauth-button)
    (should-select "#-oauth-button"))

  (it "mounts button when doc is ready"
    (render-oauth-button)
    (let [[account-id node options] (stub/last-invocation-of :render-button)]
      (should= "goog-id" account-id)
      (should= (wire/select "#-oauth-button") node)
      (should= {"oauth" "options"} (js->clj options))))

  (it "mounts button when doc is not ready"
    (with-redefs [wjs/doc-ready? (constantly false)]
      (render-oauth-button)
      (let [[node event handler] (stub/last-invocation-of :add-listener)]
        (should= js/window node)
        (should= "load" event)
        (handler))
      (let [[account-id node options] (stub/last-invocation-of :render-button)]
        (should= "goog-id" account-id)
        (should= (wire/select "#-oauth-button") node)
        (should= {"oauth" "options"} (js->clj options)))))

  (it "account id is missing"
    (wjs/o-dissoc-in! js/window ["google" "accounts" "id"])
    (render-oauth-button)
    (should-not-have-invoked :render-button)
    (should= "window.google.accounts.id doesn't exist" (log/captured-logs-str)))
  )
