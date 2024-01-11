(ns c3kit.wire.jwt
  (:require [buddy.sign.jwt :as buddy-jwt]
            [c3kit.apron.corec :as ccc]
            [c3kit.apron.time :as time]
            [ring.middleware.anti-forgery.strategy :as strategy]))

(def new-client-id
  "Generate a new client id."
  (ccc/narity (comp str ccc/new-uuid)))

(def client-id
  "Read the client id from a request."
  (comp :client-id :jwt/payload))

(defn- some-payload [& es] (some :jwt/payload es))

(defn copy-payload
  "Copy the JWT Payload from second parameter to the first."
  [to from]
  (assoc to :jwt/payload (:jwt/payload from)))

(defn update-payload
  "Applies the update function to the JWT Payload."
  [m f & args]
  (apply update m :jwt/payload f args))

(defn sign
  "Create a signed JWT token with the given claims.
   :iat and :exp claims are added will override any
   pre-existing :iat and :exp claims."
  [claims secret lifespan]
  (let [iat (time/seconds-since-epoch (time/now))
        exp (+ iat (time/millis->seconds (or lifespan (time/hours 1))))]
    (-> claims
        (assoc :iat iat)
        (assoc :exp exp)
        (buddy-jwt/sign secret))))

(defn unsign!
  "Read the payload from a JWT token.
   Throw an exception if the token is expired or if the signature is invalid."
  [token secret]
  (let [payload (buddy-jwt/unsign token secret)]
    (if (:exp payload)
      payload
      (throw (ex-info "Token is expired" {:type :validation :cause :exp})))))

(defn unsign
  "Read the payload from a JWT token.
   Return nil if the token is expired or if the signature is invalid."
  [token secret]
  (try
    (unsign! token secret)
    (catch Exception _)))

(defn- unsign-or-default [token secret]
  (or (unsign token secret)
      {:client-id (new-client-id)}))

(defn ensure-client-id
  "Decode the JWT Payload into the request
   and ensure the payload contains a client id."
  [{:keys [cookie-name secret]} request]
  (if-let [token (get-in request [:cookies cookie-name :value])]
    (-> request
        (assoc :jwt/payload (unsign-or-default token secret))
        (assoc :jwt/token token))
    (assoc-in request [:jwt/payload :client-id] (new-client-id))))

(defn- assoc-payload [response payload {:keys [cookie-name secret lifespan domain]}]
  (let [token   (sign payload secret lifespan)
        payload (unsign! token secret)
        cookie  (ccc/remove-nils {:value token :secure true :path "/" :domain domain})]
    (-> response
        (assoc :jwt/payload payload :jwt/token token)
        (assoc-in [:cookies cookie-name] cookie))))

(defn sign-response
  "If either the response or request contains a :jwt/payload,
   generate a JWT token and add it to response's cookies."
  [options request response]
  (if-let [payload (some-payload response request)]
    (assoc-payload response payload options)
    response))

(defn wrap-jwt
  "Options:
    :cookie-name - The name of the browser cookie that the JWT is stored in.
    :secret      - The secret used to sign and read the JWT token.
    :lifespan    - The time in milliseconds that the token is alive for. (default 3600000/1hr)
    :domain      - The domain of the cookie (optional)."
  [handler options]
  (fn [request]
    (let [request (ensure-client-id options request)]
      (->> request
           handler
           (sign-response options request)))))

(def get-token
  "Get the client id from the request or generate a new client id."
  (some-fn client-id new-client-id))

(defn valid-token?
  "True if the token matches the client id in the request."
  [request token]
  (some-> request client-id (= token)))

(defn write-token
  "Return the unmodified response if the token matches the request's client id.
   Otherwise, assign the token to the response's client id."
  [request response token]
  (if (= (client-id request) token)
    response
    (-> response
        (assoc :jwt/payload (some-payload response request))
        (assoc-in [:jwt/payload :client-id] token))))

; Because the anti-forgery strategy does not sign/unsign the :jwt/token,
; the anti-forgery handler must be wrapped by the wrap-handler (above)
(deftype JWTStrategy []
  strategy/Strategy
  (get-token [_ request] (get-token request))
  (valid-token? [_ request token] (valid-token? request token))
  (write-token [_ request response token] (write-token request response token)))

(def create-strategy
  "Synonym for ->JWTStrategy"
  ->JWTStrategy)
