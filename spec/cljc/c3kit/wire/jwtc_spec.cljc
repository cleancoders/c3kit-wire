(ns c3kit.wire.jwtc-spec
  (:require [speclj.core #?(:clj :refer :cljs :refer-macros) [context should-be-nil describe it should=]]
            [c3kit.wire.jwtc :as sut]))

(describe "JWT Common"

  (context "->payload"
    (it "nil token"
      (should-be-nil (sut/->payload nil)))

    (it "blank token"
      (should-be-nil (sut/->payload "")))

    (it "malformed token"
      (let [token "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmSI6IkpvaG4gRG9lIiwiaWF0IjoxNTEMjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"]
        (should-be-nil (sut/->payload token))))

    (it "has only one part"
      (let [token "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"]
        (should-be-nil (sut/->payload token))))

    (it "has only two parts"
      (let [token   "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ"
            payload {:sub "1234567890" :name "John Doe" :iat 1516239022}]
        (should= payload (sut/->payload token))))

    (it "has all three parts"
      (let [token   "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
            payload {:sub "1234567890" :name "John Doe" :iat 1516239022}]
        (should= payload (sut/->payload token))))

    (it "has underscores in the payload"
      (let [token "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyI_IjoiYWE_In0.y88bwJcmo-S3xoYBPEARz3oJkeDaHN9TbvAiOABYoxQ"
            payload {:? "aa?"}]
        (should= payload (sut/->payload token))))

    )
  )