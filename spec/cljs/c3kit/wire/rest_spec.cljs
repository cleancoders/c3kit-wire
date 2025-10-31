(ns c3kit.wire.rest-spec
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.utilc :as utilc]
            [c3kit.wire.api :as api]
            [speclj.core :refer-macros [tags focus-describe should-not-be-nil should-have-invoked stub redefs-around with-stubs should-not should context describe should-be-nil should-be it should= should-contain should-not-be]]
            [c3kit.wire.spec.spec-helperc :refer-macros [test-cljs-http-method]]
            [cljs.core.async :refer-macros [go]]
            [cljs.core.async :as async]
            [cljs-http.client :as client]
            [c3kit.wire.rest :as sut]
            [speclj.stub :as stub]))

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
    (test-cljs-http-method sut/delete! :client/delete))

  (context "API"

    (context "configuration"

      (it "merges into API"
        (sut/configure! :foo :bar)
        (should= :bar (:foo @api/config)))

      (it "wraps callback"
        (with-redefs [sut/-request! (stub :-request!)]
          (letfn [(callback [response] (prn response))
                  (f [response] (assoc response :foo :bar))]
            (sut/configure! :rest/wrap-response-fn f)
            (sut/request! ccc/noop "foo.com" {} callback)
            (let [[_ callback] (stub/last-invocation-of :-request!)]
              (should= :bar
                (-> (with-out-str (callback))
                    utilc/<-edn
                    :foo))))))
      )
    )
  )