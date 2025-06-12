(ns c3kit.wire.rest-spec
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.apron.utilc :as utilc]
            [c3kit.wire.api :as api]
            [clojure.java.io :as io]
            [speclj.core :refer :all]
            [org.httpkit.client :as client]
            [c3kit.wire.restc :as restc]
            [c3kit.wire.rest :as sut]))

(defn maybe-conj [coll x]
  (if x
    (conj coll x)
    coll))

(defmacro test-http-method [f stub & [callback]]
  `(list
     (it "sends to url with opts"
       (apply ~f (maybe-conj ["https://wire.com" {}] ~callback))
       (should-have-invoked ~stub {:times 1})
       (should-have-invoked ~stub {:with ["https://wire.com" {} ~callback]})
       (apply ~f (maybe-conj ["https://google.com" {:query-params {:a 5}}] ~callback))
       (should-have-invoked ~stub {:times 2})
       (should-have-invoked ~stub {:with ["https://google.com" {:query-params {:a 5}} ~callback]}))

     (it "converts body to json and adds content-type"
       (let [body# {:some-data [{:yes :no} 45]}]
         (apply ~f (maybe-conj ["https://example.com" {:body body#}] ~callback))
         (should-have-invoked ~stub {:with ["https://example.com"
                                            {:body (utilc/->json body#)
                                             :headers {"Content-Type" "application/json"}}
                                            ~callback]})))

     (it "doesn't override content-type of opts"
       (let [body# {:more-data 25}]
         (apply ~f (maybe-conj ["http://test.net"
                                {:body body# :headers {"Content-Type" "custom-type"}}]
                               ~callback))
         (should-have-invoked ~stub {:with ["http://test.net"
                                            {:body (utilc/->json body#)
                                             :headers {"Content-Type" "custom-type"}}
                                            ~callback]})))))

(defmacro test-http-method-sync [f stub]
  `(list
     (test-http-method ~f ~stub)

     (it "returns response"
       (should= :http-response-data (~f "https://wire.com" {})))))

(defmacro test-http-method-async [f stub]
  `(list
     (test-http-method ~f ~stub ccc/noop)

     (it "returns promise"
       (should= :http-response-data @(~f "https://wire.com" {} ccc/noop)))))

(defn request-handler [request response]
  (merge response {:request request}))

(def handle-json-request (sut/wrap-api-json-request #(request-handler % nil)))
(def handle-json-kw-request (sut/wrap-api-json-request #(request-handler % nil) {:key-words? true}))
(defn handle-json-response [body]
  (sut/wrap-api-json-response #(request-handler % body)))


(context "Rest"
  (with-stubs)

  (redefs-around [client/get (stub :client/get {:return (delay :http-response-data)})
                  client/post (stub :client/post {:return (delay :http-response-data)})
                  client/put (stub :client/put {:return (delay :http-response-data)})])

  (context "get"
    (context "synchronously"
      (test-http-method-sync sut/get! :client/get))

    (context "asynchronously"
      (test-http-method-async sut/get-async! :client/get)))

  (context "post"
    (context "synchronously"
      (test-http-method-sync sut/post! :client/post))

    (context "asynchronously"
      (test-http-method-async sut/post-async! :client/post)))

  (context "put"
    (context "synchronously"
      (test-http-method-sync sut/put! :client/put))

    (context "asynchronously"
      (test-http-method-async sut/put-async! :client/put)))

  (context "wrappers"

    (context "wrap-catch-api-errors"
      (it "default handler"
        (api/configure! :rest-on-ex nil)
        (log/capture-logs
          (let [wrapped (sut/wrap-catch-api-errors (fn [_] (throw (Exception. "test"))))]
            (should= (restc/internal-error {:message "Our apologies. An error occurred and we have been notified."})
                     (wrapped {:method :test}))
            (should= "java.lang.Exception: test" (log/captured-logs-str)))))

      (it "custom handler fn"
        (api/configure! :rest-on-ex (stub :custom-ex-handler {:return :custom-handler-response}))
        (let [e (Exception. "test")
              wrapped (sut/wrap-catch-api-errors (fn [_] (throw e)))]
          (should= :custom-handler-response (wrapped {:method :test}))
          (should-have-invoked :custom-ex-handler {:with [{:method :test} e]}))))

    (context "wrap-api-json-request"
      (it "empty request change nothing"
        (let [response (handle-json-request {})]
          (should= {} (:request response))))

      (it "request with body converts from json"
        (let [body (utilc/->json {:my-data 123})
              response (handle-json-request {:body (io/input-stream (.getBytes body))})]
          (should= {:body (utilc/<-json body)} (:request response))))

      (context "with keywords"
        (it "empty request changes nothing"
          (let [response (handle-json-kw-request {})]
            (should= {} (:request response))))

        (it "request with body converts from json with keywords"
          (let [body (utilc/->json {:my-data 123})
                response (handle-json-kw-request {:body (io/input-stream (.getBytes body))})]
            (should= {:body (utilc/<-json-kw body)} (:request response))))))

    (context "wrap-api-json-response"
      (it "empty request changes nothing"
        (let [response-handler (handle-json-response {})]
          (should-be-nil (:body (response-handler {})))))

      (it "headers but no body changes nothing"
        (let [response-handler (handle-json-response {:headers {"Hi" "bye"}})
              response (response-handler {})]
          (should-be-nil (:body response))
          (should= {"Hi" "bye"} (:headers response))))

      (context "request with body"
        (it "converts body to json"
          (let [body {:my-data 123}
                response-handler (handle-json-response {:body body})]
            (should= (utilc/->json body) (:body (response-handler {})))))

        (it "sets Content-Type to application/json"
          (let [body {:my-data 123}
                response-handler (handle-json-response {:body body})]
            (should= {"Content-Type" "application/json"} (:headers (response-handler {})))))

        (it "doesn't override Content-Type"
          (let [body {:my-data 123}
                response-handler (handle-json-response {:body body
                                                          :headers {"Content-Type" "custom-type"}})]
            (should= {"Content-Type" "custom-type"} (:headers (response-handler {})))))))

    (context "wrap-rest"
      (before (api/configure! :rest-on-ex (stub :custom-ex-handler {:return :custom-handler-response}))
              (api/configure! :version "123"))

      (it "catches api-errors"
        (let [e (Exception. "test")
              wrapped (sut/wrap-rest (fn [_] (throw e)))]
          (should= :custom-handler-response (wrapped {:method :test}))
          (should-have-invoked :custom-ex-handler {:with [{:method :test} e]})))

      (it "converts response to json"
        (let [body {:my-data 123}
              handle-json-response (fn [body] (sut/wrap-rest #(request-handler % body)))
              response-handler (handle-json-response {:body body})]
          (should= (utilc/->json (assoc body :version "123")) (:body (response-handler {})))))

      (it "adds api version"
        (let [response {:body {:hello :world}}
              handle-add-api-version (sut/wrap-rest #(request-handler % response))
              versioned-response (handle-add-api-version nil)]
          (should= (utilc/->json {:hello :world :version "123"})
                   (:body versioned-response))))

      (it "converts request from json"
        (let [body (utilc/->json {:my-data 123})
              handle-json-request (sut/wrap-rest #(request-handler % nil))
              response (handle-json-request {:body (io/input-stream (.getBytes body))})]
          (should= {:body (utilc/<-json body)} (:request response))))

      (it "converts request from json with keywords"
        (let [body (utilc/->json {:my-data 123})
              handle-json-request (sut/wrap-rest #(request-handler % nil) {:key-words? true})
              response (handle-json-request {:body (io/input-stream (.getBytes body))})]
          (should= {:body (utilc/<-json-kw body)} (:request response)))))))