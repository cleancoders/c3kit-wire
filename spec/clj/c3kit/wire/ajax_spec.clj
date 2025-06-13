(ns c3kit.wire.ajax-spec
  (:require [c3kit.apron.log :as log]
            [c3kit.apron.utilc :as utilc]
            [c3kit.wire.spec.spec-helperc :as spec-helperc]
            [c3kit.wire.ajax :as sut]
            [c3kit.wire.api :as api]
            [c3kit.wire.flashc :as flashc]
            [clojure.java.io :as io]
            [speclj.core :refer :all]))

(def handle-transit-params (sut/wrap-transit-params identity))
(def handle-transit-response (sut/wrap-api-transit-response identity))
(def handle-add-api-version (sut/wrap-add-api-version identity))

(describe "Ajax"

  (context "wrap-add-api-version"
    (it "is deprecated"
      (log/capture-logs
        (sut/wrap-add-api-version identity)
        (should= "c3kit.wire.ajax/wrap-add-api-version is deprecated. Use c3kit.wire.api/wrap-add-api-version instead."
                 (log/captured-logs-str))))

    (it "missing body"
      (should= {} (handle-add-api-version {})))

    (it "string body"
      (let [request {:body "hello"}]
        (should= request (handle-add-api-version request))))

    (it "map body"
      (api/configure! :version "123")
      (let [request  {:body {:hello :world}}
            response (handle-add-api-version request)]
        (should= (assoc-in request [:body :version] "123") response)))

    (it "vector body"
      (let [request {:body [:hello :world]}]
        (should= request (handle-add-api-version request))))
    )

  (context "wrap-api-transit-response"
    (it "empty request"
      (let [response (handle-transit-response {})]
        (should= {} response)))

    (it "string body"
      (let [response (handle-transit-response {:body "hello"})]
        (should= {:body "hello"} response)))

    (it "map body"
      (let [{:keys [body headers]} (handle-transit-response {:body {:hello :world}})]
        (should= (utilc/->transit {:hello :world}) body)
        (should= {"Content-Type" "application/transit+json; charset=utf-8"} headers)))

    (it "vector body"
      (let [request  {:body [:a :b :c]}
            response (handle-transit-response request)]
        (should= request response)))

    (it "contains headers"
      (let [{:keys [body headers]} (handle-transit-response {:body    {:hello :world}
                                                             :headers {"Foo" "Bar"}})]
        (should= (utilc/->transit {:hello :world}) body)
        (should= {"Content-Type" "application/transit+json; charset=utf-8" "Foo" "Bar"} headers)))

    (it "specifies a content type"
      (let [request  {:body    :unmodified
                      :headers {"Content-Type" :foo}}
            response (handle-transit-response request)]
        (should= request response)))
    )

  (context "wrap-transit-params"
    (it "empty request"
      (should= {} (handle-transit-params {})))

    (it "string body"
      (let [response (handle-transit-params {:body "hello"})]
        (should= {:body "hello"} response)))

    (it "transit string body"
      (let [request {:body    (utilc/->transit :hmm)
                     :headers {"content-type" "application/transit+json"}}
            {:keys [body headers params]} (handle-transit-params request)]
        (should= (:body request) body)
        (should= :hmm params)
        (should= (:headers request) headers)))

    (it "nil body"
      (let [request  {:headers {"content-type" "application/transit+json"}}
            response (handle-transit-params request)]
        (should= (assoc request :params {}) response)))

    (it "input stream"
      (let [params   {:foo :bar}
            in       (io/input-stream (.getBytes (utilc/->transit params)))
            request  {:headers {"content-type" "application/transit+json"} :body in}
            response (handle-transit-params request)]
        (should= (assoc request :params params) response)))
    )

  (context "middleware"

    (it "transfers flash messages"
      (let [handler  (fn [r] (sut/ok :foo "Yipee!"))
            wrapped  (sut/wrap-transfer-flash-to-api handler)
            response (wrapped {})]
        (should= nil (-> response :flash :messages))
        (should= "Yipee!" (-> response :body :flash first flashc/text))
        (should= true (-> response :body :flash first flashc/success?))))

    (it "adds version to response"
      (api/configure! :version "123")
      (let [handler  #(sut/ok %)
            wrapped  (sut/wrap-add-api-version handler)
            response (wrapped :foo)]
        (should= "123" (-> response :body :version))))
    )

  (context "on-error"

    (with-stubs)

    (it "default"
      (api/configure! :ajax-on-ex nil)
      (with-redefs [c3kit.wire.ajax/default-ajax-ex-handler (stub :ex-handler)]
        (let [wrapped (sut/wrap-catch-api-errors (fn [r] (throw (Exception. "test"))))]
          (wrapped {:method :test})))
      (should-have-invoked :ex-handler))

    (it "default handler"
      (api/configure! :ajax-on-ex 'c3kit.wire.ajax/default-ajax-ex-handler)
      (log/capture-logs
        (let [wrapped  (sut/wrap-catch-api-errors (fn [r] (throw (Exception. "test"))))
              response (wrapped {:method :test})]
          (should= 200 (:status response))
          (should= :error (sut/status response))
          (should= spec-helperc/default-error-message (-> response :body :flash first :text))
          (should= :error (-> response :body :flash first :level))))
      (should= "java.lang.Exception: test" (log/captured-logs-str)))

    (it "customer handler fn"
      (api/configure! :ajax-on-ex (stub :custom-ex-handler))
      (let [wrapped (sut/wrap-catch-api-errors (fn [r] (throw (Exception. "test"))))]
        (wrapped {:method :test}))
      (should-have-invoked :custom-ex-handler))
    )

  (context "helpers"

    (it "success"
      (let [response (sut/ok :foo)]
        (should= 200 (:status response))
        (should= 0 (-> response :flash :messages count))
        (should= {:status :ok :payload :foo} (:body response))))

    (it "success with flash"
      (let [response (sut/ok :bar "Cool beans!")]
        (should= 200 (:status response))
        (should= :success (-> response :body :flash first flashc/level))
        (should= "Cool beans!" (-> response :body :flash first flashc/text))
        (should= :ok (-> response sut/status))
        (should= :bar (-> response sut/payload))))

    (it "fail"
      (let [response (sut/fail :fuzz-balz "Oh Noez!")]
        (should= 200 (:status response))
        (should= :error (-> response :body :flash first flashc/level))
        (should= "Oh Noez!" (-> response :body :flash first flashc/text))
        (should= :fail (-> response sut/status))
        (should= :fuzz-balz (-> response sut/payload))))
    )

  (context "flash"

    (it "success"
      (let [response (sut/flash-success (sut/ok) "hello")
            flash    (-> response :body :flash first)]
        (should= "hello" (flashc/text flash))
        (should= :success (flashc/level flash))))

    (it "warn"
      (let [response (sut/flash-warn (sut/ok) "hello")
            flash    (-> response :body :flash first)]
        (should= "hello" (flashc/text flash))
        (should= :warn (flashc/level flash))))

    (it "error"
      (let [response (sut/flash-error (sut/ok) "hello")
            flash    (-> response :body :flash first)]
        (should= "hello" (flashc/text flash))
        (should= :error (flashc/level flash))))
    )
  )
