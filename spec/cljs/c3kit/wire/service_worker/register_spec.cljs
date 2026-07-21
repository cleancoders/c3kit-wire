(ns c3kit.wire.service-worker.register-spec
  (:require-macros [speclj.core :refer [around describe it should should= should-be-nil should-not should-contain]])
  (:require [c3kit.apron.log :as log :refer-macros [capture-logs]]
            [c3kit.wire.service-worker.fake :as fake]
            [c3kit.wire.service-worker.register :as sut]
            [speclj.core]))

(defn ->installing [state]
  (let [listeners (atom {})]
    (js-obj "state" state
            "addEventListener" (fn [ev cb] (swap! listeners assoc ev cb))
            "__fire" (fn [ev] ((get @listeners ev))))))

(defn ->registration [installing]
  (let [listeners (atom {})]
    (js-obj "installing" installing
            "addEventListener" (fn [ev cb] (swap! listeners assoc ev cb))
            "__fire" (fn [ev] ((get @listeners ev)))
            "unregister" (fn [] (fake/->resolved true)))))

(defn ->container [registration]
  (let [calls (atom [])]
    (js-obj "register" (fn [url] (swap! calls conj url) (fake/->resolved registration))
            "getRegistration" (fn [] (fake/->resolved registration))
            "__calls" calls)))

(describe "service worker registration"
  (around [it] (capture-logs (it)))

  (it "no-ops when not in a secure context"
    (let [container (->container (->registration nil))]
      (sut/register-with {:container container :secure? false :url "/sw.js"})
      (should= [] @(unchecked-get container "__calls"))))

  (it "no-ops and returns a thenable when no service worker container"
    (let [result (sut/register-with {:container nil :secure? true :url "/sw.js"})]
      (should (fn? (unchecked-get result "then")))))

  (it "registers the given url in a secure context"
    (let [container (->container (->registration nil))]
      (sut/register-with {:container container :secure? true :url "/sw.js"})
      (should-contain "/sw.js" @(unchecked-get container "__calls"))))

  (it "calls on-update when the new worker reaches installed"
    (let [installing   (->installing "installed")
          registration (->registration installing)
          container    (->container registration)
          updated      (atom nil)]
      (sut/register-with {:container container :secure? true :url "/sw.js"
                          :on-update (fn [reg] (reset! updated reg))})
      ((unchecked-get registration "__fire") "updatefound")
      ((unchecked-get installing "__fire") "statechange")
      (should= registration @updated)))

  (it "calls on-active when the new worker reaches activated"
    (let [installing   (->installing "activated")
          registration (->registration installing)
          container    (->container registration)
          active       (atom nil)]
      (sut/register-with {:container container :secure? true :url "/sw.js"
                          :on-active (fn [reg] (reset! active reg))})
      ((unchecked-get registration "__fire") "updatefound")
      ((unchecked-get installing "__fire") "statechange")
      (should= registration @active)))

  (it "unregister-with calls .unregister on the live registration"
    (let [unregistered (atom false)
          reg          (js-obj "unregister" (fn [] (reset! unregistered true) (fake/->resolved true)))
          container    (js-obj "getRegistration" (fn [] (fake/->resolved reg)))]
      (sut/unregister-with container)
      (should= true @unregistered)))

  (it "unregister-with returns a resolved thenable when no serviceWorker container"
    (let [result (sut/unregister-with nil)]
      (should (fn? (unchecked-get result "then"))))))
