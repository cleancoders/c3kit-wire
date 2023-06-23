(ns c3kit.wire.js-spec
  (:require-macros [speclj.core :refer [before context describe it redefs-around should should-be-nil should-have-invoked should-not should-not-have-invoked should= stub with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.time :as time]
            [c3kit.wire.js :as sut]
            [c3kit.wire.spec-helper :as wire]
            [speclj.core]))

(describe "JavaScript"
  (with-stubs)

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

  (context "e-files"
    (it "no target"
      (should= [] (sut/e-files (clj->js {}))))

    (it "empty file list"
      (should= [] (sut/e-files (clj->js {:target {:files []}}))))

    (it "data transfer files take precedence"
      (let [clj-file {:name "foo" :size 123}
            js-file  (clj->js clj-file)]
        (should= [(assoc clj-file :file js-file)]
                 (sut/e-files (clj->js {:dataTransfer {:files [js-file]}
                                        :target       {:files []}})))))

    (it "one file"
      (let [clj-file {:name "foo" :size 123}
            js-file  (clj->js clj-file)]
        (should= [(assoc clj-file :file js-file)]
                 (sut/e-files (clj->js {:target {:files [js-file]}})))))

    (it "two files"
      (let [clj-foo {:name "foo" :size 123}
            clj-bar {:name "bar.json" :type "application/json" :size 12345}
            js-foo  (clj->js clj-foo)
            js-bar  (clj->js clj-bar)]
        (should= [(assoc clj-foo :file js-foo)
                  (assoc clj-bar :file js-bar)]
                 (sut/e-files (clj->js {:target {:files [js-foo js-bar]}})))))

    (it "full attribute list"
      (let [now      (time/now)
            js-file  (clj->js
                       {:name               "foo.txt"
                        :size               555
                        :type               "text"
                        :lastModified       (time/millis-since-epoch now)
                        :lastModifiedDate   now
                        :webkitRelativePath "blah"})
            clj-file {:name                 "foo.txt"
                      :size                 555
                      :type                 "text"
                      :last-modified        (time/millis-since-epoch now)
                      :last-modified-date   now
                      :webkit-relative-path "blah"}]
        (should= [(assoc clj-file :file js-file)]
                 (sut/e-files (clj->js {:target {:files [js-file]}})))))
    )

  (context "clicks"
    (wire/with-root-dom)
    (before (wire/render [:div#-some-id {:on-click ccc/noop}]))
    (redefs-around [ccc/noop (stub :noop)])

    (it "clicks nothing"
      (sut/click! nil)
      (should-not-have-invoked :noop))

    (it "clicks by selector"
      (sut/click! "div#-some-id")
      (should-have-invoked :noop))

    (it "clicks by id"
      (sut/click! "-some-id")
      (should-have-invoked :noop))

    (it "clicks by node"
      (sut/click! (sut/element-by-id "-some-id"))
      (should-have-invoked :noop))
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
