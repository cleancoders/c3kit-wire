(ns c3kit.wire.restc-spec
  (:require [c3kit.apron.utilc :as utilc]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [should-not should context describe should-be-nil should-be it should= should-contain should-not-be]]
            [c3kit.wire.spec.spec-helperc #?(:clj :refer :cljs :refer-macros) [test-rest test-rest-no-params]]
            [c3kit.wire.restc :as sut]))

(describe "Rest"

  (context "response"
    (it "sets status and no headers"
      (should= {:status 123 :headers {}} (sut/response 123))
      (should= {:status 321 :headers {}} (sut/response 321)))

    (it "sets status, body, and no headers"
      (should= {:status 123 :headers {} :body {:my :body}}
               (sut/response 123 {:my :body}))
      (should= {:status 321 :headers {} :body {:more :data}}
               (sut/response 321 {:more :data})))

    (it "sets status, body, and headers"
      (should= {:status 123 :headers {"Authorization" "abc"} :body {:my :body}}
               (sut/response 123 {:my :body} {"Authorization" "abc"}))
      (should= {:status 321 :headers {"my-header" "hello"} :body {:more :data}}
               (sut/response 321 {:more :data} {"my-header" "hello"}))))

  (context "OK"
    (test-rest sut/ok 200))

  (context "Created"
    (test-rest sut/created 201))

  (context "Accepted"
    (test-rest sut/accepted 202))

  (context "Non-Authoritative Information"
    (test-rest sut/non-authoritative 203))

  (context "No Content"
    (test-rest-no-params sut/no-content 204)

    (context "with headers"

      (let [response (sut/no-content {:my-header 123})]
        (list
          (it "returns status code 204"
            (should= 204 (:status response)))

          (it "returns headers"
            (let [response (sut/no-content {:my-header 123})]
              (should= {:my-header 123} (:headers response))))))))

  (context "Bad Request"
    (test-rest sut/bad-request 400))

  (context "Unauthorized"
    (test-rest sut/unauthorized 401)

    (context "with WWW-Authenticate"
      (let [response (sut/unauthorized {:data "hi"} {:authorization "def"} "Basic")]
        (list
          (it "returns status code 401"
            (should= 401 (:status response)))

          (it "returns body"
            (should= {:data "hi"} (:body response)))

          (it "includes WWW-Authenticate in headers"
            (should= {:authorization "def"
                      "WWW-Authenticate" "Basic"} (:headers response)))))))

  (context "Payment Required"
    (test-rest sut/payment-required 402))

  (context "Forbidden"
    (test-rest sut/forbidden 403))

  (context "Not Found"
    (test-rest sut/not-found 404))

  (context "Internal Server Error"
    (test-rest sut/internal-error 500))

  (it "handles not-found error"
    (should= (sut/not-found "abc.com") (sut/not-found-handler {:uri "abc.com"})))

  (it "gets status of response"
    (should= 200 (sut/status (sut/response 200)))
    (should= 404 (sut/status (sut/response 404))))

  (it "checks status of response"
    (should (sut/success? (sut/response 200)))
    (should-not (sut/success? (sut/response 300)))
    (should (sut/success? (sut/response 201)))
    (should-not (sut/success? (sut/response 199)))

    (should (sut/error? (sut/response 400)))
    (should-not (sut/error? (sut/response 399)))
    (should (sut/error? (sut/response 401))))

  (it "gets body of response"
    (should= "I am a body" (sut/body (sut/response 200 "I am a body")))
    (should= {:map :body} (sut/body (sut/response 200 {:map :body}))))

  (it "gets un-json'd body"
    (should= "I am a body" (sut/<-json-body (sut/response 200 (utilc/->json "I am a body"))))
    (should= {"map" "body"} (sut/<-json-body (sut/response 200 (utilc/->json {:map :body})))))

  (it "gets un-json'd body with keywords"
    (should= {:map "body"} (sut/<-json-kw-body (sut/response 200 (utilc/->json {:map :body}))))
    (should= {:more "data"} (sut/<-json-kw-body (sut/response 200 (utilc/->json {:more :data})))))

  (it "gets headers of response"
    (should= {} (sut/headers (sut/response 200 nil)))
    (should= {"Authorization" "123"}
             (sut/headers (sut/response 200 nil {"Authorization" "123"}))))

  )