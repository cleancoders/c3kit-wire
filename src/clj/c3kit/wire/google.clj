(ns c3kit.wire.google
  (:import (com.google.api.client.googleapis.auth.oauth2 GoogleIdTokenVerifier GoogleIdTokenVerifier$Builder)
           (com.google.api.client.http.javanet NetHttpTransport)
           (com.google.api.client.json.gson GsonFactory)))

(defn ->GoogleIdTokenVerifier
  "Creates GoogleIdTokenVerifier from a Client ID."
  [client-id]
  (let [transport   (NetHttpTransport.)
        jsonFactory (GsonFactory.)]
    (-> (GoogleIdTokenVerifier$Builder. transport jsonFactory)
        (.setAudience (list client-id))
        (.build))))

(defn- verify [^GoogleIdTokenVerifier verifier ^String token]
  (.verify verifier ^String token))

(defn oauth-verification
  "Verifies a Google token with a client id."
  [client-id token]
  (when token
    (-> (->GoogleIdTokenVerifier client-id)
        (verify token))))

(defn token->payload
  "Decodes a Google token into a hash map."
  [id-token]
  (let [payload (.getPayload id-token)]
    {:email           (.getEmail payload)
     :name            (.get payload "name")
     :picture         (.get payload "picture")
     :email-verified? (.getEmailVerified payload)}))

(defn verify-token->payload
  "Verifies a Google token and decodes the payload into a hash map."
  [client-id token]
  (some-> (oauth-verification client-id token)
          token->payload))
