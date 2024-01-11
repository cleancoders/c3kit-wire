(ns c3kit.wire.jwt-spec
  (:require [buddy.sign.jwt :as buddy-jwt]
            [c3kit.apron.schema :as schema]
            [c3kit.apron.time :as time]
            [c3kit.wire.jwt :as sut]
            [c3kit.wire.spec-helperc :as wire-helperc]
            [ring.middleware.anti-forgery.strategy :as strategy]
            [speclj.core :refer :all])
  (:import (clojure.lang ExceptionInfo)))

(def jwt-options {:cookie-name "sid-cookie" :secret "secret-sauce" :lifespan (time/minutes 30)})
(def ensure-client-id (partial sut/ensure-client-id jwt-options))
(def sign-response (partial sut/sign-response jwt-options))
(def now (time/now))
(def sid-payload {:id 123 :email "sid@sesamestreet.com"})
(def strategy (sut/create-strategy))
(def valid-token? (partial strategy/valid-token? strategy))
(def get-token (partial strategy/get-token strategy))
(def write-token (partial strategy/write-token strategy))

(describe "JWT"
  (with-stubs)
  (wire-helperc/stub-now now)

  (it "wraps a handler"
    (let [handle      (sut/wrap-jwt identity jwt-options)
          exp         (time/seconds-since-epoch (time/after now (time/minutes 10)))
          payload     (buddy-jwt/sign (assoc sid-payload :exp exp) "secret-sauce")
          response    (handle {:cookies {"sid-cookie" {:value payload}}})
          new-payload (assoc sid-payload :iat (time/seconds-since-epoch now) :exp (time/seconds-since-epoch (time/after now (time/minutes 30))))
          cookie      (get-in response [:cookies "sid-cookie" :value])]
      (should= new-payload (:jwt/payload response))
      (should= new-payload (buddy-jwt/unsign (:jwt/token response) "secret-sauce"))
      (should= new-payload (buddy-jwt/unsign cookie "secret-sauce"))))

  (it "copy-payload"
    (should= {:jwt/payload nil} (sut/copy-payload {} {}))
    (should= {:jwt/payload nil} (sut/copy-payload {:jwt/payload {:foo :bar}} {}))
    (should= {:jwt/payload {:foo :bar}} (sut/copy-payload {} {:jwt/payload {:foo :bar}}))
    (should= {:jwt/payload {:foo :bar}} (sut/copy-payload {:jwt/payload {:bar :foo}} {:jwt/payload {:foo :bar}})))

  (it "update-payload"
    (should= {:jwt/payload {:foo :bar}} (sut/update-payload {} assoc :foo :bar))
    (should= {:jwt/payload "foo"} (sut/update-payload {:jwt/payload :foo} name))
    (should= {:jwt/payload 6} (sut/update-payload {:jwt/payload 0} + 1 2 3)))

  (context "unsign!"
    (it "a missing exp claim mimics buddy-signs behavior with an expired exp claim"
      (let [secret "secret-sauce"
            token  (buddy-jwt/sign sid-payload secret)]
        (try
          (sut/unsign! token secret)
          (should-fail "Expected unsign! to throw")
          (catch ExceptionInfo e
            (should= "Token is expired" (ex-message e))
            (should= {:type :validation :cause :exp} (ex-data e))))))

    (it "expired exp claim"
      (let [secret  "secret-sauce"
            exp     (time/seconds-since-epoch (time/before now (time/minutes 5)))
            payload (assoc sid-payload :exp exp)
            token   (buddy-jwt/sign payload secret)]
        (should-throw ExceptionInfo (str "Token is expired (" exp ")") (sut/unsign! token secret))))

    (it "unexpired exp claim"
      (let [secret  "secret-sauce"
            exp     (time/seconds-since-epoch (time/after now (time/minutes 5)))
            payload (assoc sid-payload :exp exp)
            token   (buddy-jwt/sign payload secret)]
        (should= payload (sut/unsign! token secret))))
    )

  (context "ensuring client ids in requests"
    (it "missing token"
      (-> {} ensure-client-id :jwt/payload :client-id parse-uuid should))

    (it "malformed token"
      (let [response (ensure-client-id {:cookies {"sid-cookie" {:value "bad token"}}})]
        (should= {"sid-cookie" {:value "bad token"}} (:cookies response))
        (should= "bad token" (:jwt/token response))
        (-> response :jwt/payload :client-id parse-uuid should)))

    (it "empty map in payload"
      (let [token    (buddy-jwt/sign {} "secret-sauce")
            response (ensure-client-id {:cookies {"sid-cookie" {:value token}}})]
        (should= token (get-in response [:cookies "sid-cookie" :value]))
        (should= token (:jwt/token response))
        (-> response :jwt/payload :client-id parse-uuid should)))

    (it "payload missing exp"
      (let [token    (buddy-jwt/sign sid-payload "secret-sauce")
            response (ensure-client-id {:cookies {"sid-cookie" {:value token}}})]
        (should= token (get-in response [:cookies "sid-cookie" :value]))
        (should= token (:jwt/token response))
        (-> response :jwt/payload :client-id parse-uuid should)))

    (it "user data in payload"
      (let [payload  (assoc sid-payload :exp (time/seconds-since-epoch (time/after now (time/minutes 10))))
            token    (buddy-jwt/sign payload "secret-sauce")
            response (ensure-client-id {:cookies {"sid-cookie" {:value token}}})]
        (should= token (get-in response [:cookies "sid-cookie" :value]))
        (should= token (:jwt/token response))
        (should= payload (:jwt/payload response)))))

  (context "signing responses"
    (it "missing jwt/payload in request and response"
      (let [response (sign-response {} {})]
        (should-not (:jwt/payload response))))

    (it "missing jwt/payload in response"
      (let [response (sign-response {:jwt/payload {:client-id "123"}} {})
            iat      (time/seconds-since-epoch now)
            exp      (time/seconds-since-epoch (time/after now (time/minutes 30)))
            {:keys [value secure path]} (get-in response [:cookies "sid-cookie"])]
        (should= (buddy-jwt/sign {:client-id "123" :iat iat :exp exp} "secret-sauce") value)
        (should secure)
        (should= "/" path)))

    (it "contains jwt/payload in response"
      (let [response (sign-response {} {:jwt/payload sid-payload})
            iat      (time/seconds-since-epoch now)
            exp      (time/seconds-since-epoch (time/after now (time/minutes 30)))
            token    (get-in response [:cookies "sid-cookie" :value])]
        (should= (assoc sid-payload :iat iat :exp exp) (:jwt/payload response))
        (should= (buddy-jwt/unsign token "secret-sauce") (:jwt/payload response))))

    (it "does not specify a lifespan"
      (let [sign-response (partial sut/sign-response (dissoc jwt-options :lifespan))
            response      (sign-response {} {:jwt/payload sid-payload})
            iat           (time/seconds-since-epoch now)
            exp           (time/seconds-since-epoch (time/after now (time/hours 1)))
            token         (get-in response [:cookies "sid-cookie" :value])]
        (should= (assoc sid-payload :iat iat :exp exp) (:jwt/payload response))
        (should= (buddy-jwt/unsign token "secret-sauce") (:jwt/payload response))))

    (it "specifies a domain"
      (let [sign-response (partial sut/sign-response (assoc jwt-options :domain ".example.com"))
            response      (sign-response {} {:jwt/payload sid-payload})
            cookie        (get-in response [:cookies "sid-cookie"])]
        (should-be string? (:value cookie))
        (should= true (:secure cookie))
        (should= "/" (:path cookie))
        (should= ".example.com" (:domain cookie))))

    (it "does not specify a domain"
      (let [sign-response (partial sut/sign-response jwt-options)
            response      (sign-response {} {:jwt/payload sid-payload})
            cookie        (get-in response [:cookies "sid-cookie"])]
        (should-be string? (:value cookie))
        (should= true (:secure cookie))
        (should= "/" (:path cookie))
        (should-not-contain :domain cookie)))
    )

  (context "get-token"
    (it "retrieves the client id from the JWT payload"
      (should= "client-id" (get-token {:jwt/payload {:client-id "client-id"}}))
      (should= "something else" (get-token {:jwt/payload {:client-id "something else"}})))

    (it "generates a uuid when there is no client id"
      (let [token (get-token {})]
        (schema/->uuid token)
        (should (string? token)))))

  (context "valid-token?"
    (it "invalid when there is no client id"
      (should-not (valid-token? {} "token"))
      (should-not (valid-token? {:jwt/payload {:client-id nil}} "token")))

    (it "invalid when the token doesn't match what's expected"
      (should-not (valid-token? {:jwt/payload {:client-id "fake"}} "token")))

    (it "valid when the token matches what's expected"
      (should (valid-token? {:jwt/payload {:client-id "token"}} "token"))
      (should (valid-token? {:jwt/payload {:client-id "abc123"}} "abc123"))))

  (context "write-token"
    (it "does nothing when the token matches the request"
      (let [request  {:jwt/payload {:client-id "abc123"}}
            response (write-token request {} "abc123")]
        (should= {} response)))

    (it "adds the token to the response when it doesn't match the request"
      (let [request  {:jwt/payload {:user-id 123 :client-id "123abc"}}
            response (write-token request {} "abc123")]
        (should= {:jwt/payload {:user-id 123 :client-id "abc123"}} response)))

    (it "does not overwrite the jwt payload if it already exists"
      (let [request  {:jwt/payload {:user-id 123 :client-id "123abc"}}
            response (write-token request {:jwt/payload {:user-id 1}} "abc123")]
        (should= {:jwt/payload {:user-id 1 :client-id "abc123"}} response))))
  )
