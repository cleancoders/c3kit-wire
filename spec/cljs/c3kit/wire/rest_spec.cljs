(ns c3kit.wire.rest-spec
  (:require [speclj.core :refer-macros [tags focus-describe should-not-be-nil should-have-invoked stub redefs-around with-stubs should-not should context describe should-be-nil should-be it should= should-contain should-not-be]]
            [c3kit.wire.spec.spec-helperc :refer-macros [test-cljs-http-method]]
            [cljs.core.async :refer-macros [go]]
            [cljs.core.async :as async]
            [cljs-http.client :as client]
            [c3kit.wire.rest :as sut]))

(defn cljs-http-response [& _]
  (async/chan))

(describe "Rest"
  (with-stubs)

  (redefs-around [client/get    (stub :client/get {:invoke cljs-http-response})
                  client/post   (stub :client/post {:invoke cljs-http-response})
                  client/put    (stub :client/put {:invoke cljs-http-response})
                  client/delete (stub :client/delete {:invoke cljs-http-response})])

  (context "get!"
    (test-cljs-http-method sut/get! :client/get))

  (context "post!"
    (test-cljs-http-method sut/post! :client/post))

  (context "put!"
    (test-cljs-http-method sut/put! :client/put))

  (context "delete!"
    (test-cljs-http-method sut/delete! :client/delete)))