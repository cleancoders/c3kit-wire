(ns c3kit.wire.spec.spec-helperc
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.utilc :as utilc]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [stub should-have-invoked context should-be-nil it should=]]))

(def default-error-message "Our apologies. An error occurred and we have been notified.")

#?(:clj (defmacro test-rest-no-params [f status-code]
          `(context "no params"

             (let [response# (~f)]
               (list
                 (it (str "returns status code " ~status-code)
                   (should= ~status-code (:status response#)))

                 (it "returns no body"
                   (should-be-nil (:body response#)))

                 (it "returns no headers"
                   (should= {} (:headers response#))))))))

#?(:clj (defmacro test-rest [f status-code]
          `(list

             (test-rest-no-params ~f ~status-code)

             (context "with body"

               (let [response# (~f {:hello :goodbye})]
                 (list
                   (it (str "returns status code " ~status-code)
                     (should= ~status-code (:status response#)))

                   (it "returns body"
                     (should= {:hello :goodbye} (:body response#)))

                   (it "returns no headers"
                     (should= {} (:headers response#))))))

             (context "with body and headers"

               (let [response# (~f {:my-key 5} {:authorization "abc"})]
                 (list
                   (it (str "returns status code " ~status-code)
                     (should= ~status-code (:status response#)))

                   (it "returns body"
                     (should= {:my-key 5} (:body response#)))

                   (it "returns headers"
                     (should= {:authorization "abc"} (:headers response#)))))))))

(defn maybe-conj [coll x]
  (if x
    (conj coll x)
    coll))

(defn no-conj [coll _x]
  coll)

#?(:clj
   (defmacro test-http-method [f stub-name & [include-callback? callback]]
     `(let [conj-fn# (if ~include-callback? conj no-conj)]
        (list
          (it "sends to url with opts"
            (apply ~f (maybe-conj ["https://wire.com" {}] ~callback))
            (should-have-invoked ~stub-name {:times 1})
            (should-have-invoked ~stub-name {:with (conj-fn# ["https://wire.com" {}] ~callback)})
            (apply ~f (maybe-conj ["https://google.com" {:query-params {:a 5}}] ~callback))
            (should-have-invoked ~stub-name {:times 2})
            (should-have-invoked ~stub-name {:with (conj-fn# ["https://google.com" {:query-params {:a 5}}] ~callback)}))

          (it "converts body to json and adds content-type"
            (let [body# {:some-data [{:yes :no} 45]}]
              (apply ~f (maybe-conj ["https://example.com" {:body body#}] ~callback))
              (should-have-invoked ~stub-name {:with (conj-fn#
                                                       ["https://example.com"
                                                        {:body    (utilc/->json body#)
                                                         :headers {"Content-Type" "application/json"}}]
                                                       ~callback)})))

          (it "doesn't override content-type of opts"
            (let [body# {:more-data 25}]
              (apply ~f (maybe-conj ["http://test.net"
                                     {:body body# :headers {"Content-Type" "custom-type"}}]
                                    ~callback))
              (should-have-invoked ~stub-name {:with (conj-fn#
                                                       ["http://test.net"
                                                        {:body    (utilc/->json body#)
                                                         :headers {"Content-Type" "custom-type"}}]
                                                       ~callback)})))

          (it "includes single, keyword cookie"
            (apply ~f (maybe-conj ["https://example.com" {:cookies {:cookie-1 {:value "val1"}}}] ~callback))
            (should-have-invoked ~stub-name {:with (conj-fn#
                                                     ["https://example.com"
                                                      {:headers {"Cookie" "cookie-1=val1"}}]
                                                     ~callback)}))

          (it "includes single, string cookie"
            (apply ~f (maybe-conj ["https://example.com" {:cookies {"cookie-1" {:value "val1"}}}] ~callback))
            (should-have-invoked ~stub-name {:with (conj-fn#
                                                     ["https://example.com"
                                                      {:headers {"Cookie" "cookie-1=val1"}}]
                                                     ~callback)}))

          (it "includes multiple cookies"
            (apply ~f (maybe-conj ["https://example.com" {:cookies {"cookie-1" {:value "val1"}
                                                                    :cookie-2  {:value "val2"}}}] ~callback))
            (should-have-invoked ~stub-name {:with (conj-fn#
                                                     ["https://example.com"
                                                      {:headers {"Cookie" "cookie-2=val2;cookie-1=val1"}}]
                                                     ~callback)}))))))

#?(:clj
   (defmacro test-http-method-sync [f stub-name stub-response]
     `(list
        (test-http-method ~f ~stub-name true)

        (it "returns response"
          (should= ~stub-response (~f "https://wire.com" {}))))))

#?(:clj
   (defmacro test-http-method-async [f stub-name stub-response]
     `(list
        (test-http-method ~f ~stub-name true ccc/noop)

        (it "returns promise if no callback"
          (should= ~stub-response @(~f "https://wire.com" {})))

        (it "calls callback and returns nil"
          (with-redefs [ccc/noop (stub :callback)]
            (should-be-nil @(~f "https://wire.com" {} ccc/noop))
            (should-have-invoked :callback {:with [~stub-response]}))))))

#?(:clj
   (defmacro test-cljs-http-method [f stub-name]
     `(list
        (test-http-method ~f ~stub-name false)

        (it "returns nil"
          (should-be-nil (~f "http://test.com" {:query-params {:a 5}} ccc/noop))))))