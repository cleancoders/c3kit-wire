(ns c3kit.wire.spec-helper
  (:require [c3kit.wire.apic :as apic]
            [c3kit.wire.flashc :as flashc]
            [c3kit.apron.log :as log]
            [speclj.core :refer :all]))

(log/warn!)

(defmacro should-redirect-to [response location]
  `(let [response# ~response]
     (should= 302 (:status response#))
     (should= ~location ((:headers response#) "Location"))))

(defmacro should-ajax-redirect-to
  ([response location]
   `(let [response# ~response]
      (should= :redirect (-> response# :body :status))
      (should= ~location (-> response# :body :uri))))
  ([response location message]
   `(let [response# ~response]
      (should= :redirect (-> response# :body :status))
      (should= ~location (-> response# :body :uri))
      (should= ~message (-> response# :body apic/first-flash-text)))))

(defmacro should-be-ajax-ok [response message]
  `(let [response# ~response]
     (should= :ok (-> response# :body :status))
     (should= ~message (-> response# :body apic/first-flash-text))
     (should (-> response# :body apic/first-flash flashc/success?))))

(defmacro should-be-ajax-fail [response message]
  `(let [response# ~response]
     (should= :fail (-> response# :body :status))
     (should= ~message (-> response# :body apic/first-flash-text))
     (should (-> response# :body apic/first-flash flashc/error?))))

(defmacro should-be-ws-fail [response message]
  `(let [response# ~response]
     (should= :fail (-> response# :status))
     (should= ~message (-> response# apic/first-flash-text))
     (should (-> response# apic/first-flash flashc/error?))))
