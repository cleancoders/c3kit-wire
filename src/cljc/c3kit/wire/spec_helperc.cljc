(ns c3kit.wire.spec-helperc
  #?(:cljs (:require-macros [speclj.core :refer [-fail -to-s around]]))
  (:require [c3kit.apron.log :as log]
            [speclj.stub :as stub]
            #?(:clj  [speclj.core :refer :all]
               :cljs [speclj.core])))

#?(:clj (defmacro stub-now [time]
          `(around [it#]
             (with-redefs [c3kit.apron.time/now (stub :now {:return ~time})]
               (it#)))))

#?(:clj (defmacro with-utc-offset [millis]
          `(around [it#]
             (with-redefs [c3kit.apron.time/utc-offset (constantly ~millis)]
               (it#)))))

#?(:clj (defmacro should-select
          "Asserts the selector finds a node"
          ([selector]
           `(let [value# ~selector
                  node#  (c3kit.wire.spec-helper/select value#)]
              (when-not node#
                (-fail (str "Expected selector to find node: " (-to-s value#))))))
          ([root selector]
           `(let [value# ~selector
                  node#  (c3kit.wire.spec-helper/select ~root value#)]
              (when-not node#
                (-fail (str "Expected selector to find node: " (-to-s value#))))))))

#?(:clj (defmacro should-not-select
          "Asserts the selector does not find a node"
          ([selector]
           `(let [value# ~selector
                  node#  (c3kit.wire.spec-helper/select value#)]
              (when node#
                (-fail (str "Expected selector NOT to find node: " (-to-s value#))))))
          ([root selector]
           `(let [value# ~selector
                  node#  (c3kit.wire.spec-helper/select ~root value#)]
              (when node#
                (-fail (str "Expected selector NOT to find node: " (-to-s value#))))))))

#?(:clj (defmacro should-have-invoked-ws
          "Asserts the invocation of ws/call!"
          ([id] `(should= ~id (c3kit.wire.spec-helper/last-ws-call-id)))
          ([id params]
           `(do (should= ~id (c3kit.wire.spec-helper/last-ws-call-id))
                (should= ~params (c3kit.wire.spec-helper/last-ws-call-params))))
          ([id params handler]
           `(do (should= ~id (c3kit.wire.spec-helper/last-ws-call-id))
                (should= ~params (c3kit.wire.spec-helper/last-ws-call-params))
                (should= ~handler (c3kit.wire.spec-helper/last-ws-call-handler))))))

#?(:clj (defmacro should-have-invoked-ajax-post
          "Asserts the invocation of ajax/post!"
          ([url] `(should= ~url (c3kit.wire.spec-helper/last-ajax-post-url)))
          ([url params]
           `(do (should= ~url (c3kit.wire.spec-helper/last-ajax-post-url))
                (should= ~params (c3kit.wire.spec-helper/last-ajax-post-params))))
          ([url params handler]
           `(do (should= ~url (c3kit.wire.spec-helper/last-ajax-post-url))
                (should= ~params (c3kit.wire.spec-helper/last-ajax-post-params))
                (should= ~handler (c3kit.wire.spec-helper/last-ajax-post-handler))))))

#?(:clj (defmacro should-not-have-invoked-ajax-post
          "Asserts the invocation of ajax/post! is not present"
          ([url] `(should-not-contain ~url (map first (stub/invocations-of :ajax/post!))))
          ([url params] `(should-not-contain [~url ~params] (map (partial take 2) (stub/invocations-of :ajax/post!))))
          ([url params handler] `(should-not-contain [~url ~params ~handler] (map (partial take 3) (stub/invocations-of :ajax/post!))))))

#?(:clj (defmacro should-have-invoked-ajax-get
          "Asserts the invocation of ajax/get!"
          ([url] `(should= ~url (c3kit.wire.spec-helper/last-ajax-get-url)))
          ([url params]
           `(do (should= ~url (c3kit.wire.spec-helper/last-ajax-get-url))
                (should= ~params (c3kit.wire.spec-helper/last-ajax-get-params))))
          ([url params handler]
           `(do (should= ~url (c3kit.wire.spec-helper/last-ajax-get-url))
                (should= ~params (c3kit.wire.spec-helper/last-ajax-get-params))
                (should= ~handler (c3kit.wire.spec-helper/last-ajax-get-handler))))))

#?(:clj (defmacro should-not-have-invoked-ajax-get
          "Asserts the invocation of ajax/get! is not present"
          ([url] `(should-not-contain ~url (map first (stub/invocations-of :ajax/get!))))
          ([url params] `(should-not-contain [~url ~params] (map (partial take 2) (stub/invocations-of :ajax/get!))))
          ([url params handler] `(should-not-contain [~url ~params ~handler] (map (partial take 3) (stub/invocations-of :ajax/get!))))))

#?(:clj (defmacro redefs-around-logs [bindings]
          `(around [it#]
             (with-redefs ~bindings
               (log/capture-logs (it#))))))
