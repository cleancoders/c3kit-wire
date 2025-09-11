(ns c3kit.wire.rest-spec
  (:require [c3kit.apron.log :as log]
            [c3kit.apron.utilc :as utilc]
            [c3kit.wire.api :as api]
            [c3kit.wire.rest :as sut]
            [c3kit.wire.restc :as restc]
            [c3kit.wire.spec.spec-helperc :as spec-helperc]
            [c3kit.wire.spec.spec-helperc :refer [test-http-method-async test-http-method-sync]]
            [clojure.java.io :as io]
            [org.httpkit.client :as client]
            [speclj.core :refer :all]))

(defn request-handler [request response]
  (merge response {:request request}))

(def handle-json-request (sut/wrap-api-json-request #(request-handler % nil)))
(def handle-json-kw-request (sut/wrap-api-json-request #(request-handler % nil) {:keywords? true}))
(defn handle-json-response [response]
  (sut/wrap-api-json-response #(request-handler % response)))

(defn handle-wrap-rest [body & [opts]]
  (sut/wrap-rest #(request-handler % body) opts))

(def http-stub-response :http-response-data)

(defn httpkit-response [_url _opts callback]
  (delay
    (if callback
      (do (callback http-stub-response)
          nil)
      http-stub-response)))

(context "Rest"
  (with-stubs)

  (redefs-around [client/get    (stub :client/get {:invoke httpkit-response})
                  client/post   (stub :client/post {:invoke httpkit-response})
                  client/put    (stub :client/put {:invoke httpkit-response})
                  client/delete (stub :client/delete {:invoke httpkit-response})])

  (context "get"
    (context "synchronously"
      (test-http-method-sync sut/get! :client/get http-stub-response))

    (context "asynchronously"
      (test-http-method-async sut/get-async! :client/get http-stub-response)))

  (context "post"
    (context "synchronously"
      (test-http-method-sync sut/post! :client/post http-stub-response))

    (context "asynchronously"
      (test-http-method-async sut/post-async! :client/post http-stub-response)))

  (context "put"
    (context "synchronously"
      (test-http-method-sync sut/put! :client/put http-stub-response))

    (context "asynchronously"
      (test-http-method-async sut/put-async! :client/put http-stub-response)))

  (context "delete"
    (context "synchronously"
      (test-http-method-sync sut/delete! :client/delete http-stub-response))

    (context "asynchronously"
      (test-http-method-async sut/delete-async! :client/delete http-stub-response)))

  (context "wrappers"

    (context "wrap-catch-rest-errors"
      (around [it] (log/with-level :report (it)))

      (it "default handler"
        (api/configure! :rest-on-ex nil)
        (log/capture-logs
          (let [wrapped (sut/wrap-catch-rest-errors (fn [_] (throw (Exception. "test"))))]
            (should= (restc/internal-error {:message spec-helperc/default-error-message})
                     (wrapped {:method :test}))
            (should= "java.lang.Exception: test" (log/captured-logs-str)))))

      (it "custom handler fn"
        (api/configure! :rest-on-ex (stub :custom-ex-handler {:return :custom-handler-response}))
        (let [e       (Exception. "test")
              wrapped (sut/wrap-catch-rest-errors (fn [_] (throw e)))]
          (should= :custom-handler-response (wrapped {:method :test}))
          (should-have-invoked :custom-ex-handler {:with [{:method :test} e]})))
      )

    (context "wrap-api-json-request"
      (it "empty request change nothing"
        (let [response (handle-json-request {})]
          (should= {} (:request response))))

      (it "request with body converts from json"
        (let [body     (utilc/->json {:my-data 123})
              response (handle-json-request {:body (io/input-stream (.getBytes body))})]
          (should= {:body (utilc/<-json body)} (:request response))))

      (it "logs errors if invalid json"
        (log/capture-logs
          (let [body     "{bleh"
                response (handle-json-request {:body (io/input-stream (.getBytes body))})]
            (should= (restc/bad-request) response)
            (should= "Couldn't parse as JSON: {bleh" (log/captured-logs-str)))))

      (context "with keywords"
        (it "empty request changes nothing"
          (let [response (handle-json-kw-request {})]
            (should= {} (:request response))))

        (it "request with body converts from json with keywords"
          (let [body     (utilc/->json {:my-data 123})
                response (handle-json-kw-request {:body (io/input-stream (.getBytes body))})]
            (should= {:body (utilc/<-json-kw body)} (:request response))))

        (it "logs errors if invalid json"
          (log/capture-logs
            (let [body     "{bleh"
                  response (handle-json-kw-request {:body (io/input-stream (.getBytes body))})]
              (should= (restc/bad-request) response)
              (should= "Couldn't parse as JSON: {bleh" (log/captured-logs-str)))))))

    (context "wrap-api-json-response"
      (it "empty request changes nothing"
        (let [response-handler (handle-json-response {})]
          (should-be-nil (:body (response-handler {})))))

      (it "headers but no body changes nothing"
        (let [response-handler (handle-json-response {:headers {"Hi" "bye"}})
              response         (response-handler {})]
          (should-be-nil (:body response))
          (should= {"Hi" "bye"} (:headers response))))

      (context "request with body"
        (it "converts body to json"
          (let [body             {:my-data 123}
                response-handler (handle-json-response {:body body})]
            (should= (utilc/->json body) (:body (response-handler {})))))

        (it "sets Content-Type to application/json"
          (let [body             {:my-data 123}
                response-handler (handle-json-response {:body body})]
            (should= {"Content-Type" "application/json"} (:headers (response-handler {})))))

        (it "doesn't override Content-Type"
          (let [body             {:my-data 123}
                response-handler (handle-json-response {:body    body
                                                        :headers {"Content-Type" "custom-type"}})]
            (should= {"Content-Type" "custom-type"} (:headers (response-handler {})))))))

    (context "wrap-rest"
      (before (api/configure! :rest-on-ex (stub :custom-ex-handler {:return :custom-handler-response}))
              (api/configure! :version "123"))

      (it "catches api-errors"
        (let [e       (Exception. "test")
              wrapped (sut/wrap-rest (fn [_] (throw e)))]
          (should= :custom-handler-response (wrapped {:method :test}))
          (should-have-invoked :custom-ex-handler {:with [{:method :test} e]})))

      (it "converts response to json"
        (let [body             {:my-data 123}
              response-handler (handle-wrap-rest {:body body})]
          (should= (utilc/->json (assoc body :version "123")) (:body (response-handler {})))))

      (it "adds api version"
        (let [response               {:body {:hello :world}}
              handle-add-api-version (handle-wrap-rest response)]
          (should= (utilc/->json {:hello :world :version "123"})
                   (:body (handle-add-api-version nil)))))

      (it "converts request from json"
        (let [body                (utilc/->json {:my-data 123})
              handle-json-request (handle-wrap-rest nil)
              response            (handle-json-request {:body (io/input-stream (.getBytes body))})]
          (should= {:body (utilc/<-json body)} (:request response))))

      (it "logs errors if invalid json"
        (log/capture-logs
          (let [body                "{bleh"
                handle-json-request (handle-wrap-rest nil)
                response            (handle-json-request {:body (io/input-stream (.getBytes body))})]
            (should= (restc/bad-request) response)
            (should= "Couldn't parse as JSON: {bleh" (log/captured-logs-str)))))

      (it "converts request from json with keywords"
        (let [body                (utilc/->json {:my-data 123})
              handle-json-request (handle-wrap-rest nil {:keywords? true})
              response            (handle-json-request {:body (io/input-stream (.getBytes body))})]
          (should= {:body (utilc/<-json-kw body)} (:request response)))))))