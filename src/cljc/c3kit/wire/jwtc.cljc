(ns c3kit.wire.jwtc
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.utilc :as utilc]
            [clojure.string :as str]
            #?(:clj [cheshire.core :as json])
            #?(:clj [buddy.core.codecs :as codecs])))

#?(:cljs
   (defn- uri-encode [v]
     (as-> v $
           (ccc/first-char-code $)
           (utilc/->hex $)
           (ccc/pad-left! $ 2 0)
           (str "%" $))))

#?(:cljs
   (defn- ->uri-component [s]
     (->> s
          (js-invoke js/window "atob")
          (map uri-encode)
          (apply str))))

(def ^:private b64-replacements {\- "+" \_ "/"})

(defn- token->b64-payload [token]
  (some-> token
          (str/split #"\.")
          second
          (str/escape b64-replacements)))

(defn- decode-b64 [b64-payload]
  #?(:cljs (-> b64-payload
               ->uri-component
               js/decodeURIComponent
               utilc/<-json-kw)
     :clj  (-> b64-payload
               codecs/b64->str
               (json/parse-string true))))

(defn ->payload
  "Returns the decoded payload of a JWT token or nil."
  [token]
  (when-let [payload (token->b64-payload token)]
    (try
      (decode-b64 payload)
      (catch #?(:clj Exception :cljs :default) _))))
