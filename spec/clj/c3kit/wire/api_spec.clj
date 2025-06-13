(ns c3kit.wire.api-spec
  (:require [speclj.core :refer :all]
            [c3kit.wire.api :as sut]))

(def handle-add-api-version (sut/wrap-add-api-version identity))

(describe "Api"

  (context "wrap-add-api-version"
    (it "missing body"
      (should= {} (handle-add-api-version {})))

    (it "string body"
      (let [request {:body "hello"}]
        (should= request (handle-add-api-version request))))

    (it "map body"
      (sut/configure! :version "123")
      (let [request  {:body {:hello :world}}
            response (handle-add-api-version request)]
        (should= (assoc-in request [:body :version] "123") response)))

    (it "vector body"
      (let [request {:body [:hello :world]}]
        (should= request (handle-add-api-version request))))))