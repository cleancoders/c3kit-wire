(ns c3kit.wire.rest-spec
  (:require [c3kit.wire.api :as api]
            [c3kit.wire.flash :as flash]
            [speclj.core :refer-macros [around before tags focus-describe should-not-be-nil should-have-invoked stub redefs-around with-stubs should-not should context describe should-be-nil should-be it should= should-contain should-not-be with]]
            [c3kit.wire.spec.spec-helperc :refer-macros [test-cljs-http-method]]
            [cljs.core.async :refer-macros [go]]
            [cljs.core.async :as async]
            [cljs-http.client :as client]
            [c3kit.wire.rest :as sut]))

(declare handler)
(defn callback [resp] (inc (:body resp)))
(defn midware-callback [opts resp] (callback resp))
(def uri "foo.com")
(def request {})
(def response (atom nil))

(defn plus-2 [n] (+ n 2))
(defn plus-3 [n] (+ n 3))

(defn should-contain-flash [msg]
  (should-contain (dissoc msg :id) (map #(dissoc % :id) @flash/state)))

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

      )

    (redefs-around [sut/-request! (stub :request {:invoke (fn [_ callback] (callback @response))})])
    (before (reset! api/config {})
            (reset! response {:status 200 :body 1}))

    (it "passes through handler without middleware"
      (should= 2 (sut/get! url request callback)))

    (context "middleware"

      (context "wrap-success-handler"

        (it "doesn't invoke for unsuccessful response"
          (let [handler  (sut/wrap-success-handler midware-callback)
                response {:status 400}]
            (should-be-nil (handler {} response))))

        (it "invokes callback for successful response"
          (let [handler (sut/wrap-success-handler midware-callback)]
            (doseq [n (range 200 300)]
              (should= 2 (handler {} (assoc @response :status n))))))

        (it "invokes with response body"
          (let [handler (sut/wrap-success-handler (fn [_opts body] (+ 2 body)))]
            (should= 3 (handler {:rest/unwrap-body? true} @response))))
        )

      (context "wrap-response-code"

        (it "does not invoke callback"
          (let [handler (sut/wrap-response-code 400 (comp plus-2 :body) midware-callback)]
            (should= 2 (handler {} @response))))

        (it "invokes callback"
          (let [handler (sut/wrap-response-code 200 (comp plus-2 :body) midware-callback)]
            (should= 3 (handler {} @response))))

        (it "invokes with response body"
          (let [handler (sut/wrap-response-code 200 plus-2 midware-callback)]
            (should= 3 (handler {:rest/unwrap-body? true} @response))))

        (it "passes through opts"
          (let [middleware (partial sut/wrap-response-code 200 (comp plus-2 :body))]
            (sut/configure! :rest/response-middleware middleware)
            (should= 3 (sut/get! url request callback :rest/unwrap-body? false))))
        )

      (context "wrap-response-handlers"

        (with handler (sut/wrap-response-codes
                        {400 (comp plus-2 :body)
                         401 (comp plus-3 :body)}
                        midware-callback))

        (it "does not invoke middleware"
          (should= 2 (@handler {} @response)))

        (it "does invoke middleware"
          (should= 3 (@handler {} (assoc @response :status 400))))

        (it "invokes with response body"
          (let [callback (fn [_opts body] (inc body))
                handler  (sut/wrap-response-codes {200 plus-2} callback)]
            (should= 3 (handler {:rest/unwrap-body? true} @response))))

        (it "passes through opts"
          (let [middleware (partial sut/wrap-response-codes {200 (comp plus-2 :body)})]
            (sut/configure! :rest/response-middleware middleware)
            (should= 3 (sut/get! url request callback :rest/unwrap-body? false))))
        )

      (context "wrap-user-handlers"

        (before (sut/configure! :rest/response-middleware sut/wrap-user-handlers))

        (it "does not invoke handler"
          (should= 2 (sut/get! url request callback (sut/with-handlers 400 (comp plus-2 :body)))))

        (it "invokes handler"
          (should= 3 (sut/get! url request callback (sut/with-handlers 200 (comp plus-2 :body)))))

        (it "invokes with response body"
          (should= 3 (sut/get! url request callback (sut/with-handlers 200 plus-2) :rest/unwrap-body? true)))

        (it "prefers user handlers to configured handlers"
          (sut/configure! :rest/response-middleware (comp
                                                       sut/wrap-user-handlers
                                                       (partial sut/wrap-response-codes {200 plus-2})))


          (should= 4 (sut/get! url request callback (sut/with-handlers 200 plus-3) :rest/unwrap-body? true)))
        )
      )
    )
  )