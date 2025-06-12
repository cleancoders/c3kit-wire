(ns c3kit.wire.spec.spec-helperc
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.utilc :as utilc]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [should-have-invoked context should-be-nil it should=]]))

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
                     (should= {:hello :goodbye}  (:body response#)))

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

#?(:clj
  (defmacro test-http-method [f stub & [callback]]
    `(list
       (it "sends to url with opts"
         (apply ~f (maybe-conj ["https://wire.com" {}] ~callback))
         (should-have-invoked ~stub {:times 1})
         (should-have-invoked ~stub {:with ["https://wire.com" {} ~callback]})
         (apply ~f (maybe-conj ["https://google.com" {:query-params {:a 5}}] ~callback))
         (should-have-invoked ~stub {:times 2})
         (should-have-invoked ~stub {:with ["https://google.com" {:query-params {:a 5}} ~callback]}))

       (it "converts body to json and adds content-type"
         (let [body# {:some-data [{:yes :no} 45]}]
           (apply ~f (maybe-conj ["https://example.com" {:body body#}] ~callback))
           (should-have-invoked ~stub {:with ["https://example.com"
                                              {:body (utilc/->json body#)
                                               :headers {"Content-Type" "application/json"}}
                                              ~callback]})))

       (it "doesn't override content-type of opts"
         (let [body# {:more-data 25}]
           (apply ~f (maybe-conj ["http://test.net"
                                  {:body body# :headers {"Content-Type" "custom-type"}}]
                                 ~callback))
           (should-have-invoked ~stub {:with ["http://test.net"
                                              {:body (utilc/->json body#)
                                               :headers {"Content-Type" "custom-type"}}
                                              ~callback]}))))))

#?(:clj
  (defmacro test-http-method-sync [f stub stub-response]
    `(list
       (test-http-method ~f ~stub)

       (it "returns response"
         (should= ~stub-response (~f "https://wire.com" {}))))))

#?(:clj
  (defmacro test-http-method-async [f stub stub-response]
    `(list
       (test-http-method ~f ~stub ccc/noop)

       (it "returns promise"
         (should= ~stub-response @(~f "https://wire.com" {} ccc/noop))))))