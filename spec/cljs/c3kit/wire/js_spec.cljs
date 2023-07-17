(ns c3kit.wire.js-spec
  (:require-macros [speclj.core :refer [before context describe it redefs-around should should-be-nil should-have-invoked should-not should-not-have-invoked should= stub with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.time :as time]
            [c3kit.wire.js :as sut]
            [c3kit.wire.spec-helper :as wire]
            [speclj.core]))

(defn test-modifier [name modified? attr]
  (it name
    (should= nil (modified? (clj->js {})))
    (should= false (modified? (clj->js {attr false})))
    (should= true (modified? (clj->js {attr true})))))

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

  (context "key modifiers"
    (test-modifier "alt" sut/alt-key? :altKey)
    (test-modifier "meta (command)" sut/meta-key? :metaKey)
    (test-modifier "control" sut/ctrl-key? :ctrlKey)
    (test-modifier "shift" sut/shift-key? :shiftKey))

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

    (it "nothing"
      (sut/click! nil)
      (should-not-have-invoked :noop))

    (it "by selector"
      (sut/click! "div#-some-id")
      (should-have-invoked :noop))

    (it "by id"
      (sut/click! "-some-id")
      (should-have-invoked :noop))

    (it "by node"
      (sut/click! (sut/element-by-id "-some-id"))
      (should-have-invoked :noop))
    )

  (it "keys"
    (should= "Backspace" sut/BACKSPACE)
    (should= "Tab" sut/TAB)
    (should= "Enter" sut/ENTER)
    (should= "ShiftLeft" sut/SHIFTLEFT)
    (should= "ShiftRight" sut/SHIFTRIGHT)
    (should= "Escape" sut/ESC)
    (should= "Space" sut/SPACE)
    (should= "ArrowLeft" sut/LEFT)
    (should= "ArrowUp" sut/UP)
    (should= "ArrowRight" sut/RIGHT)
    (should= "ArrowDown" sut/DOWN)
    (should= "Delete" sut/DELETE)
    (should= "Digit0" sut/DIGIT0)
    (should= "Digit1" sut/DIGIT1)
    (should= "Digit2" sut/DIGIT2)
    (should= "Digit3" sut/DIGIT3)
    (should= "Digit4" sut/DIGIT4)
    (should= "Digit5" sut/DIGIT5)
    (should= "Digit6" sut/DIGIT6)
    (should= "Digit7" sut/DIGIT7)
    (should= "Digit8" sut/DIGIT8)
    (should= "Digit9" sut/DIGIT9)
    (should= "NumpadAdd" sut/NUMPAD+)
    (should= "Comma" sut/COMMA))

  (it "knows what keys were pressed"
    (letfn [(->event [code] (clj->js {:code code}))]
      (should-not (sut/BACKSPACE? (->event "backspace")))
      (should (sut/BACKSPACE? (->event "Backspace")))
      (should (sut/TAB? (->event "Tab")))
      (should (sut/ENTER? (->event "Enter")))
      (should (sut/SHIFT? (->event "ShiftLeft")))
      (should (sut/SHIFT? (->event "ShiftRight")))
      (should (sut/ESC? (->event "Escape")))
      (should (sut/SPACE? (->event "Space")))
      (should (sut/LEFT? (->event "ArrowLeft")))
      (should (sut/UP? (->event "ArrowUp")))
      (should (sut/RIGHT? (->event "ArrowRight")))
      (should (sut/DOWN? (->event "ArrowDown")))
      (should (sut/DELETE? (->event "Delete")))
      (should (sut/NUMPAD+? (->event "NumpadAdd")))
      (should (sut/COMMA? (->event "Comma")))))
  )
