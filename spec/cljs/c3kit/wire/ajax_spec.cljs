(ns c3kit.wire.ajax-spec
  (:require-macros [c3kit.apron.log :refer [capture-logs]]
                   [speclj.core :refer [around context describe it should should-contain
                                        should-have-invoked should-not should= stub with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.ajax :as sut]
            [c3kit.wire.api :as api]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.js :as cc]
            [c3kit.wire.spec-helper :as helper]
            [cljs-http.client :as http]
            [speclj.core]
            [speclj.stub :as stub]))

(describe "AJAX"
  (with-stubs)

  (it "server-down?"
    (should-not (sut/server-down? {:status     200
                                   :success    true
                                   :error-code :no-error
                                   :error-text ""}))
    (should (sut/server-down? {:status     0
                               :success    false
                               :error-code :http-error
                               :error-text " [0]"}))
    (should (sut/server-down? {:status     502
                               :success    false
                               :error-code :http-error
                               :error-text "Bad Gateway [502]"})))

  (context "triage-response"

    (around [it]
      (with-redefs [api/handle-api-response (stub :handle-api-response)
                    sut/handle-server-down (stub :handle-server-down)
                    sut/handle-http-error (stub :handle-unknown)]
        (it)))

    (it "success"
      (sut/triage-response {:error-code :no-error :status 200} {})
      (should-have-invoked :handle-api-response))

    (it "server-down"
      (sut/triage-response {:error-code :http-error :status 0} {})
      (should-have-invoked :handle-server-down))

    (it "unknown"
      (sut/triage-response {:error-code :no-error :status 123} {})
      (should-have-invoked :handle-unknown))
    )

  (context "handle server-down"

    (around [it]
      (with-redefs [cc/timeout (stub :timeout)]
        (log/capture-logs
          (it))))

    (it "flash"
      (should= true (:persist api/server-down-flash))
      (should= false (flash/active? api/server-down-flash))
      (sut/handle-server-down {})
      (should= true (flash/active? api/server-down-flash))
      )

    (it "timeout"
      (sut/handle-server-down {})
      (should-have-invoked :timeout)) ; presumably to re-invoke the api call
    )

  (it "params-type"
    (should= :transit-params (sut/params-key {:params {:foo "bar"}}))
    (should= :transit-params (sut/params-key {:params {:foo "bar"} :options {:params-type :transit}}))
    (should= :query-params (sut/params-key {:params {:foo "bar"} :options {:params-type :query}}))
    (should= :form-params (sut/params-key {:params {:foo "bar"} :options {:params-type :form}}))
    (should= :edn-params (sut/params-key {:params {:foo "bar"} :options {:params-type :edn}}))
    (should= :json-params (sut/params-key {:params {:foo "bar"} :options {:params-type :json}}))
    (should= :multipart-params (sut/params-key {:params {:foo "bar"} :options {:params-type :multipart}})))

  (it "GET and HEAD use :query-params because they can't have body"
    (should= :query-params (sut/params-key {:params {:foo "bar"} :method "GET"}))
    (should= :query-params (sut/params-key {:params {:foo "bar"} :method "HEAD"}))
    (should= :query-params (sut/params-key {:params {:foo "bar"} :method "GET" :options {:params-type :transit}}))
    (should= :query-params (sut/params-key {:params {:foo "bar"} :method "HEAD" :options {:params-type :transit}})))

  (context "requests"
    (helper/stub-ajax)

    (it "headers"
      (let [req (sut/request-map (sut/build-ajax-call "GET" ccc/noop "/some/url" {} ccc/noop [:headers {"foo" "bar"}]))]
        (should-contain "foo" (:headers req))
        (should= "bar" (get-in req [:headers "foo"]))))

    (it "params default to transit"
      (let [req (sut/request-map (sut/build-ajax-call "POST" ccc/noop "/some/url" {:foo "bar"} ccc/noop []))]
        (should= {:transit-params {:foo "bar"}} req)))

    (it "pass-through-keys"
      (let [req (sut/request-map {:options {:accept            "accept"
                                            :basic-auth        "basic-auth"
                                            :content-type      "content-type"
                                            :default-headers   "default-headers"
                                            :headers           "headers"
                                            :method            "method"
                                            :oauth-token       "oauth-token"
                                            :with-credentials? "with-credentials?"
                                            :transit-opts      "transit-opts"}})]
        (should= "accept" (:accept req))
        (should= "basic-auth" (:basic-auth req))
        (should= "content-type" (:content-type req))
        (should= "default-headers" (:default-headers req))
        (should= "headers" (:headers req))
        (should= "method" (:method req))
        (should= "oauth-token" (:oauth-token req))
        (should= "with-credentials?" (:with-credentials? req))
        (should= "transit-opts" (:transit-opts req))))
    )

  (it "on-http-error"
    (let [ajax-call (sut/build-ajax-call "POST" ccc/noop "/some/url" {} ccc/noop
                                         [:on-http-error (stub :unexpected-response-handler)])
          response  {:status 413 :body "foo"}]
      (capture-logs
        (sut/triage-response response ajax-call))
      (should-have-invoked :unexpected-response-handler {:with [response]})))

  (context "main api"

    (around [it] (with-redefs [sut/-do-ajax-request (stub :-do-ajax-request)] (it)))

    (it "get!"
      (sut/get! "/endpoint" {:foo "bar"} ccc/noop)
      (let [[call] (stub/last-invocation-of :-do-ajax-request)]
        (should= "GET" (:method call))
        (should= http/get (:method-fn call))))

    (it "post!"
      (sut/post! "/endpoint" {:foo "bar"} ccc/noop)
      (let [[call] (stub/last-invocation-of :-do-ajax-request)]
        (should= "POST" (:method call))
        (should= http/post (:method-fn call))))

    (it "request!"
      (sut/request! :foo "/endpoint" {:foo "bar"} ccc/noop)
      (let [[call] (stub/last-invocation-of :-do-ajax-request)]
        (should= "FOO" (:method call))))

    )

  (it "wrap-csrf"
    (with-redefs [api/config (delay {:ajax-prep-fn (sut/prep-csrf "X-CSRF-Token" "foobar")})]
      (let [request (sut/request-map (sut/build-ajax-call "GET" ccc/noop "/endpoint" {} ccc/noop []))]
        (should= "foobar" (get-in request [:headers "X-CSRF-Token"])))))
  )
