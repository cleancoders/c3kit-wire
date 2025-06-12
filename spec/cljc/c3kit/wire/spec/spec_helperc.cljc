(ns c3kit.wire.spec.spec-helperc
  (:require [speclj.core #?(:clj :refer :cljs :refer-macros) [context should-be-nil it should=]]))

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
