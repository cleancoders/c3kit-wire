(ns c3kit.wire.js-spec
  (:require-macros [speclj.core :refer [context describe it should should-be-nil should-not should=]])
  (:require [c3kit.apron.time :as time]
            [c3kit.wire.js :as sut]
            [speclj.core]))

(describe "JavaScript"

  (it "->query-string"
    (should= "" (sut/->query-string nil))
    (should= "" (sut/->query-string {}))
    (should= "hello=%3Aworld" (sut/->query-string {:hello :world}))
    (should= "goodbye=cruel%20world" (sut/->query-string {"goodbye" "cruel world"}))
    (should= "one=1&two=2&pi=3.1415926" (sut/->query-string {:one 1 :two 2 :pi 3.1415926}))
    (should= "nothing%3F=" (sut/->query-string {:nothing? nil}))
    (should= "date=%23inst%20%222022-03-04T23%3A59%3A02.000-00%3A00%22" (sut/->query-string {:date (time/parse :ref3339 "2022-03-04T23:59:02-00:00")})))

  (it "encode-url"
    (should= "root" (sut/encode-url "root" nil))
    (should= "root" (sut/encode-url "root" {}))
    (should= "root?foo=%3Abar" (sut/encode-url "root" {:foo :bar})))

  (context "cookies"
    (it "nil"
      (with-redefs [sut/doc-cookie (constantly nil)]
        (should= {} (sut/cookies))))

    (it "empty"
      (with-redefs [sut/doc-cookie (constantly "")]
        (should= {} (sut/cookies))))

    (it "one cookie"
      (with-redefs [sut/doc-cookie (constantly "hello=world")]
        (should= {"hello" "world"} (sut/cookies))))

    (it "two cookies"
      (with-redefs [sut/doc-cookie (constantly "foo=bar; cheese=whiz; ")]
        (should= {"foo" "bar" "cheese" "whiz"} (sut/cookies))))

    (it "three cookies"
      (with-redefs [sut/doc-cookie (constantly "foo=bar; hello=world; cheese=whiz; ")]
        (should= {"hello" "world" "foo" "bar" "cheese" "whiz"} (sut/cookies))))
    )

  (it "keys"
    (should= 8 sut/BACKSPACE)
    (should= 9 sut/TAB)
    (should= 13 sut/ENTER)
    (should= 16 sut/SHIFT)
    (should= 27 sut/ESC)
    (should= 32 sut/SPACE)
    (should= 37 sut/LEFT)
    (should= 38 sut/UP)
    (should= 39 sut/RIGHT)
    (should= 40 sut/DOWN)
    (should= 46 sut/DELETE)
    (should= 48 sut/DIGIT0)
    (should= 49 sut/DIGIT1)
    (should= 50 sut/DIGIT2)
    (should= 51 sut/DIGIT3)
    (should= 52 sut/DIGIT4)
    (should= 53 sut/DIGIT5)
    (should= 54 sut/DIGIT6)
    (should= 55 sut/DIGIT7)
    (should= 56 sut/DIGIT8)
    (should= 57 sut/DIGIT9)
    (should= 107 sut/NUMPAD+)
    (should= 188 sut/COMMA))

  (it "knows what keys were pressed"
    (letfn [(->event [key-code] (clj->js {:keyCode key-code}))]
      (should-not (sut/BACKSPACE? (->event 9)))
      (should (sut/BACKSPACE? (->event 8)))
      (should (sut/TAB? (->event 9)))
      (should (sut/ENTER? (->event 13)))
      (should (sut/SHIFT? (->event 16)))
      (should (sut/ESC? (->event 27)))
      (should (sut/SPACE? (->event 32)))
      (should (sut/LEFT? (->event 37)))
      (should (sut/UP? (->event 38)))
      (should (sut/RIGHT? (->event 39)))
      (should (sut/DOWN? (->event 40)))
      (should (sut/DELETE? (->event 46)))
      (should (sut/NUMPAD+? (->event 107)))
      (should (sut/COMMA? (->event 188)))))

  )
