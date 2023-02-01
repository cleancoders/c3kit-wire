(ns c3kit.wire.jwtc
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.utilc :as utilc]
            [clojure.string :as s]
            #?(:clj [cheshire.core :as json])
            #?(:clj [buddy.core.codecs :as codecs])
            #?(:clj [buddy.core.codecs.base64 :as b64])))

#?(:cljs
   (defn- ->uri-component [s]
     (->> s
          (.atob js/window)
          seq
          (sequence
            (comp
              (map ccc/first-char-code)
              (map utilc/->hex)
              (map #(ccc/pad-left! % 2 0))
              (map #(str "%" %))))
          (s/join ""))))

(defn ->payload
  "Returns the decoded payload of a JWT token or nil."
  [token]
  (when-let [payload (some-> token (s/split #"\.") second)]
    (try
      #?(:cljs
         (-> payload
             (s/replace #"-" "+")
             (s/replace #"_" "/")
             ->uri-component
             js/decodeURIComponent
             utilc/<-json
             (update-keys keyword))
         :clj
         (-> (b64/decode payload)
             codecs/bytes->str
             (json/parse-string true)))
      (catch #?(:clj Exception :cljs :default) _))))
