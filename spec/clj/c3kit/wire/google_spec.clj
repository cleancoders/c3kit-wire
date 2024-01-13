(ns c3kit.wire.google-spec
  (:require [c3kit.wire.google :as sut]
            [speclj.core :refer :all])
  (:import (com.google.api.client.googleapis.auth.oauth2 GoogleIdToken$Payload)
           (com.google.api.client.json.webtoken JsonWebToken JsonWebToken$Header)))


(describe "Google OAuth"
  (with-stubs)

  (redefs-around [sut/->GoogleIdTokenVerifier (stub :->GoogleIdTokenVerifier {:return :verifier})])

  (context "verifying and decoding a token"

    (it "valid"
      (with-redefs [sut/verify (stub :verify {:invoke (fn [_verifier token] token)})]
        (let [payload  (doto (new GoogleIdToken$Payload)
                         (.setEmail "fluffy@pup.com")
                         (.setEmailVerified false)
                         (.set "name" "Fluffy")
                         (.set "picture" "woof.jpg"))
              id-token (new JsonWebToken (new JsonWebToken$Header) payload)
              decoded  (sut/verify-token->payload "client-id" id-token)]
          (should-have-invoked :->GoogleIdTokenVerifier {:with ["client-id"]})
          (should-have-invoked :verify {:with [:verifier id-token]})
          (should= "fluffy@pup.com" (:email decoded))
          (should= "Fluffy" (:name decoded))
          (should= "woof.jpg" (:picture decoded))
          (should= false (:email-verified? decoded)))))

    (it "invalid"
      (with-redefs [sut/verify (constantly nil)]
        (let [payload  (doto (new GoogleIdToken$Payload)
                         (.setEmail "fluffy@pup.com")
                         (.setEmailVerified false)
                         (.set "name" "Fluffy")
                         (.set "picture" "woof.jpg"))
              id-token (new JsonWebToken (new JsonWebToken$Header) payload)]
          (should-be-nil (sut/verify-token->payload "client-id" id-token)))))
    )

  (context "oauth verification"
    (it "missing token"
      (should-be-nil (sut/oauth-verification "client-id" nil)))

    (it "verification fails"
      (with-redefs [sut/verify (stub :verify {:return nil})]
        (should-be-nil (sut/oauth-verification "client-id" "some-token"))
        (should-have-invoked :->GoogleIdTokenVerifier {:with ["client-id"]})
        (should-have-invoked :verify {:with [:verifier "some-token"]})))

    (it "verification succeeds"
      (with-redefs [sut/verify (stub :verify {:invoke (fn [_verifier token] token)})]
        (should= "some-token" (sut/oauth-verification "client-id" "some-token"))
        (should-have-invoked :->GoogleIdTokenVerifier {:with ["client-id"]})
        (should-have-invoked :verify {:with [:verifier "some-token"]})))
    )
  
  (context "decoding a token"

    (it "email not verified"
      (let [payload  (doto (new GoogleIdToken$Payload)
                       (.setEmail "fluffy@pup.com")
                       (.setEmailVerified false)
                       (.set "name" "Fluffy")
                       (.set "picture" "woof.jpg"))
            id-token (new JsonWebToken (new JsonWebToken$Header) payload)
            decoded  (sut/token->payload id-token)]
        (should= "fluffy@pup.com" (:email decoded))
        (should= "Fluffy" (:name decoded))
        (should= "woof.jpg" (:picture decoded))
        (should= false (:email-verified? decoded))))

    (it "email verified"
      (let [payload  (doto (new GoogleIdToken$Payload)
                       (.setEmail "kitty@cat.com")
                       (.setEmailVerified true)
                       (.set "name" "Kitty")
                       (.set "picture" "mew.jpg"))
            id-token (new JsonWebToken (new JsonWebToken$Header) payload)
            decoded  (sut/token->payload id-token)]
        (should= "kitty@cat.com" (:email decoded))
        (should= "Kitty" (:name decoded))
        (should= "mew.jpg" (:picture decoded))
        (should= true (:email-verified? decoded))))
    )

  (context "verifies and decodes the token"
    (it "missing token"
      (should-be-nil (sut/oauth-verification "client-id" nil)))

    (it "verification fails"
      (with-redefs [sut/verify (stub :verify {:return nil})]
        (should-be-nil (sut/oauth-verification "client-id" "some-token"))
        (should-have-invoked :->GoogleIdTokenVerifier {:with ["client-id"]})
        (should-have-invoked :verify {:with [:verifier "some-token"]})))

    (it "verification succeeds"
      (with-redefs [sut/verify (stub :verify {:invoke (fn [_verifier token] token)})]
        (should= "some-token" (sut/oauth-verification "client-id" "some-token"))
        (should-have-invoked :->GoogleIdTokenVerifier {:with ["client-id"]})
        (should-have-invoked :verify {:with [:verifier "some-token"]})))
    )

  )
