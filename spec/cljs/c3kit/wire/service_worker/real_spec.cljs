(ns c3kit.wire.service-worker.real-spec
  "Real-browser smoke test: drives the SUT's synchronous security surface
   (cacheable?, ->fallback) against genuine js/Request / js/Response / js/Headers
   in the Chromium harness — no fakes. Catches divergence between the in-memory
   fakes and real browser object semantics (header filtering, URL origin parsing,
   status->ok derivation). Async strategies stay fake-covered: real Promises are
   async and the synchronous resolved-value helper cannot drive them here."
  (:require-macros [speclj.core :refer [context describe it should should= should-not]])
  (:require [c3kit.wire.service-worker.core :as sut]
            [speclj.core]))

(def origin "https://app.test")

(defn real-ctx []
  {:scope (js-obj "location" (js-obj "origin" origin))})

(defn real-request [url & [{:keys [method headers credentials]}]]
  (js/Request. url
               (clj->js (cond-> {:method (or method "GET")}
                          headers     (assoc :headers headers)
                          credentials (assoc :credentials credentials)))))

(defn real-response [body & [{:keys [status headers]}]]
  (js/Response. body (clj->js (cond-> {:status (or status 200)}
                                headers (assoc :headers headers)))))

(def real-context?
  (and (exists? js/Request) (exists? js/Response) (exists? js/Headers) (exists? js/URL)))

(describe "cacheable? against real browser objects"
  (if-not real-context?
    (it "SKIPPED — Request/Response/Headers/URL unavailable in this runtime" (should-not real-context?))

    (context "real js/Request + js/Response"
      (it "true for same-origin ok GET"
        (should= true (sut/cacheable? (real-ctx) (real-request (str origin "/img.png")) (real-response "x") {})))

      (it "false for non-GET"
        (should= false (sut/cacheable? (real-ctx) (real-request (str origin "/x") {:method "POST"}) (real-response "x") {})))

      (it "false for non-ok response (status derives ok)"
        (should= false (sut/cacheable? (real-ctx) (real-request (str origin "/x")) (real-response "x" {:status 500}) {})))

      (it "false for Cache-Control: no-store"
        (should= false (sut/cacheable? (real-ctx) (real-request (str origin "/x")) (real-response "x" {:headers {"Cache-Control" "no-store"}}) {})))

      (it "false for Cache-Control: no-cache"
        (should= false (sut/cacheable? (real-ctx) (real-request (str origin "/x")) (real-response "x" {:headers {"Cache-Control" "no-cache"}}) {})))

      (it "false for Cache-Control: private"
        (should= false (sut/cacheable? (real-ctx) (real-request (str origin "/x")) (real-response "x" {:headers {"Cache-Control" "private, max-age=0"}}) {})))

      (it "false for Vary: Cookie"
        (should= false (sut/cacheable? (real-ctx) (real-request (str origin "/x")) (real-response "x" {:headers {"Vary" "Cookie"}}) {})))

      (it "false for Vary: *"
        (should= false (sut/cacheable? (real-ctx) (real-request (str origin "/x")) (real-response "x" {:headers {"Vary" "*"}}) {})))

      (it "false for cross-origin by default, true when allowed"
        (let [x (real-request "https://cdn.other/x.png")]
          (should= false (sut/cacheable? (real-ctx) x (real-response "x") {}))
          (should= true  (sut/cacheable? (real-ctx) x (real-response "x") {:allow-cross-origin true}))))

      (it "false for credentials: include by default, true when allowed"
        (let [c (real-request (str origin "/x") {:credentials "include"})]
          (should= false (sut/cacheable? (real-ctx) c (real-response "x") {}))
          (should= true  (sut/cacheable? (real-ctx) c (real-response "x") {:cache-credentialed true}))))

      (it "false for Authorization header by default, true when allowed"
        (let [a (real-request (str origin "/x") {:headers {"Authorization" "Bearer t"}})]
          (should= false (sut/cacheable? (real-ctx) a (real-response "x") {}))
          (should= true  (sut/cacheable? (real-ctx) a (real-response "x") {:cache-credentialed true}))))

              ;; CHARACTERIZATION — documents a real-browser limitation, NOT desired behavior.
              ;; Set-Cookie is a forbidden response-header name (Fetch spec): browsers strip it
              ;; from JS-visible Response.headers, so headers.get("Set-Cookie") returns nil and
              ;; the set-cookie? guard is inert in production. The fake exposes Set-Cookie, so the
              ;; fake spec's "false when Set-Cookie present" tests a path that cannot occur live.
              ;; Real protection for cookie-set/cookie-auth endpoints must come from routing them
              ;; to network-only — see the cacheable? docstring's threat-model note.
      (it "real browsers hide Set-Cookie from headers.get, so the guard cannot fire"
        (let [r (real-response "x" {:headers {"Set-Cookie" "sid=abc"}})]
          (should= nil (.get (.-headers r) "Set-Cookie"))
          (should= true (sut/cacheable? (real-ctx) (real-request (str origin "/x")) r {})))))))

(describe "->fallback against real js/Response"
  (if-not real-context?
    (it "SKIPPED — Response unavailable in this runtime" (should-not real-context?))

    (context "real 503"
      (it "synthesizes a real 503 Response with ok=false"
        (let [r (sut/->fallback {} (real-request (str origin "/x")))]
          (should= 503 (.-status r))
          (should= false (.-ok r))))

      (it "returns a provided fallback response unchanged"
        (let [r (real-response "down")]
          (should= r (sut/->fallback {:fallback r} (real-request (str origin "/x"))))))

      (it "calls a fallback fn with the request"
        (let [req (real-request (str origin "/x"))]
          (should= req (sut/->fallback {:fallback (fn [rq] rq)} req)))))))
