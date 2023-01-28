(ns c3kit.wire.spec-helper
  (:require [c3kit.apron.log :as log]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.apic :as apic]
            [c3kit.wire.flashc :as flashc]
            [speclj.core :refer :all]))

(log/warn!)

(defmacro should-redirect-to [response location]
  `(let [response# ~response]
     (should= 302 (:status response#))
     (should= ~location ((:headers response#) "Location"))))

(defmacro should-ajax-redirect-to
  ([response location]
   `(let [response# ~response]
      (should= :redirect (ajax/status response#))
      (should= ~location (-> response# :body :uri))))
  ([response location message]
   `(let [response# ~response]
      (should= :redirect (ajax/status response#))
      (should= ~location (-> response# :body :uri))
      (should= ~message (-> response# :body apic/first-flash-text)))))

(defmacro should-be-ajax-ok [response message]
  `(let [response# ~response]
     (should= :ok (ajax/status response#))
     (should= ~message (-> response# :body apic/first-flash-text))
     (should (-> response# :body apic/first-flash flashc/success?))))

(defmacro should-be-ajax-ok-payload [response payload]
  `(let [response# ~response]
     (should= :ok (ajax/status response#))
     (should= ~payload (ajax/payload response#))))

(defmacro should-be-ajax-fail [response message]
  `(let [response# ~response]
     (should= :fail (ajax/status response#))
     (should= ~message (-> response# :body apic/first-flash-text))
     (should (-> response# :body apic/first-flash flashc/error?))))

(defmacro should-be-ws-fail [response message]
  `(let [response# ~response]
     (should= :fail (-> response# :status))
     (should= ~message (-> response# apic/first-flash-text))
     (should (-> response# apic/first-flash flashc/error?))))
