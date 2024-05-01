(ns c3kit.wire.js-spec
  (:require-macros [speclj.core :refer [before context describe it redefs-around should should-end-with should-have-invoked should-not should-not-have-invoked should= stub with-stubs]])
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

(defn test-user-agent [user-agent expected pred]
  (it user-agent
    (with-redefs [sut/user-agent (constantly user-agent)]
      (should= expected (pred)))))

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

  (it "o-update!"
    (let [obj (clj->js {:value 1})]
      (sut/o-update! obj "value" + 1)
      (should= 2 (sut/o-get obj "value"))
      (sut/o-update! obj "value" + 1 2 3)
      (should= 8 (sut/o-get obj "value"))))

  (it "o-dissoc!"
    (let [obj (clj->js {:child {:grandchild {:value 1}}})]
      (should= {} (js->clj (sut/o-dissoc! obj "child")))
      (should= obj (sut/o-dissoc! obj "blah"))))

  (it "o-assoc-in!"
    (let [obj (clj->js {:child {:grandchild {:value 1}}})]
      (sut/o-assoc-in! obj ["blah"] 1)
      (should= 1 (.-blah obj))
      (sut/o-assoc-in! obj ["child" "grandchild" "value"] 2)
      (should= 2 (-> obj .-child .-grandchild .-value))
      (sut/o-assoc-in! obj ["child" "grandchild" "greatGrandchild"] "thing")
      (should= "thing" (-> obj .-child .-grandchild .-greatGrandchild))
      (sut/o-assoc-in! obj ["does" "not" "exist"] "thing")
      (should= "thing" (-> obj .-does .-not .-exist))))

  (it "o-dissoc-in!"
    (let [obj (clj->js {:child {:grandchild {:value 1}}})]
      (should= {"child" {"grandchild" {}}} (js->clj (sut/o-dissoc-in! obj ["child" "grandchild" "value"])))
      (should= obj (sut/o-dissoc-in! obj ["does" "not" "exist"]))
      (sut/o-dissoc-in! obj ["child"])
      (should= {} (js->clj obj))))

  (it "encode-url"
    (should= "root" (sut/encode-url "root" nil))
    (should= "root" (sut/encode-url "root" {}))
    (should= "root?foo=%3Abar" (sut/encode-url "root" {:foo :bar})))

  (context "audio"

    (it "create"
      (let [audio (sut/->audio "foo.mp3")]
        (should= js/HTMLAudioElement (type audio))
        (should-end-with "foo.mp3" (sut/o-get audio "src"))))

    (it "play"
      (let [audio (js-obj "play" (stub :play))]
        (sut/play-audio audio)
        (should-have-invoked :play)))

    (it "pause"
      (let [audio (js-obj "pause" (stub :pause))]
        (sut/pause-audio audio)
        (should-have-invoked :pause)))
    )

  (it "stringify-json"
    (should= "null" (sut/stringify-json nil))
    (should= "{\n  \"foo\": \"bar\"\n}" (sut/stringify-json {:foo :bar}))
    (should= "{\n  \"biz\": \"baz\"\n}" (sut/stringify-json {"foo" "bar" "biz" "baz"} ["biz"]))
    (should= "{\n  \"foo\": \"replaced\",\n  \"dee\": \"dum\"\n}" (sut/stringify-json {"foo" "bar" "dee" "dum"} {"foo" "replaced"}))
    (should= "{\n  \"foo\": null\n}" (sut/stringify-json {"foo" "bar"} {"foo" nil}))
    (should= "\"blah\"" (sut/stringify-json {"foo" "bar"} (constantly "blah")))
    (should= "{\"foo\":\"bar\"}" (sut/stringify-json {"foo" "bar"} nil 0)))

  (it "parse-json"
    (should= nil (sut/parse-json nil))
    (should= nil (sut/parse-json "null"))
    (should= {"foo" "bar"} (sut/parse-json "{\n  \"foo\": \"bar\"\n}"))
    (should= {"foo" "bar" "biz" "baz"} (sut/parse-json "{\"foo\": \"bar\", \"biz\": \"baz\"}"))
    (should= {"foo" "replaced" "dee" "dum"} (sut/parse-json "{\"foo\": \"bar\", \"dee\": \"dum\"}" {"foo" "replaced"}))
    (should= "blah" (sut/parse-json "{\"foo\": \"bar\"}" (constantly "blah"))))

  (context "active elements"
    (wire/with-root-dom)
    (before (wire/render [:div
                          [:input#-text-1]
                          [:input#-text-2]]))

    (it "detects an active and inactive element"
      (with-redefs [sut/active-element (constantly (wire/select "#-text-1"))]
        (should (sut/active? "#-text-1"))
        (should-not (sut/inactive? "#-text-1"))
        (should-not (sut/active? "#-text-2"))
        (should (sut/inactive? "#-text-2")))))

  (context "key modifiers"
    (test-modifier "alt" sut/alt-key? :altKey)
    (test-modifier "meta (command)" sut/meta-key? :metaKey)
    (test-modifier "control" sut/ctrl-key? :ctrlKey)
    (test-modifier "shift" sut/shift-key? :shiftKey))

  (it "reads raw user agent"
    (let [navigator (clj->js {:userAgent "raw user agent"})]
      (should= "raw user agent" (sut/user-agent navigator))))

  (context "detects macOS"
    (test-user-agent "Mozilla/5.0 (Windows; foo) Netscape6/6.1" false sut/mac-os?)
    (test-user-agent "Mozilla/5.0 (Macintosh; foo) Netscape6/6.1" true sut/mac-os?)
    (test-user-agent "Mozilla/5.0 (MacIntel; foo) Netscape6/6.1" true sut/mac-os?)
    (test-user-agent "Mozilla/5.0 (MacPPC; foo) Netscape6/6.1" true sut/mac-os?)
    (test-user-agent "Mozilla/5.0 (Mac68K; foo) Netscape6/6.1" true sut/mac-os?)
    (test-user-agent "Mozilla/5.0 (Macintel; foo) Netscape6/6.1" false sut/mac-os?)
    (test-user-agent "Mozilla/5.0 (macOS; foo) Netscape6/6.1" false sut/mac-os?)
    (test-user-agent "Mozilla/5.0 (macOS; Macintosh) Netscape6/6.1" false sut/mac-os?))

  (context "detects Windows"
    (test-user-agent "Mozilla/5.0 (Macintosh; foo) Netscape6/6.1" false sut/win-os?)
    (test-user-agent "Mozilla/5.0 (Windows; foo) Netscape6/6.1" true sut/win-os?)
    (test-user-agent "Mozilla/5.0 (Win32; foo) Netscape6/6.1" true sut/win-os?)
    (test-user-agent "Mozilla/5.0 (Win64; foo) Netscape6/6.1" true sut/win-os?)
    (test-user-agent "Mozilla/5.0 (WinCE; foo) Netscape6/6.1" true sut/win-os?)
    (test-user-agent "Mozilla/5.0 (Wince; foo) Netscape6/6.1" false sut/win-os?)
    (test-user-agent "Mozilla/5.0 (windows; foo) Netscape6/6.1" false sut/win-os?)
    (test-user-agent "Mozilla/5.0 (Win; Windows) Netscape6/6.1" false sut/win-os?))

  (context "detects Linux"
    (test-user-agent "Mozilla/5.0 (Macintosh; foo) Netscape6/6.1" false sut/linux?)
    (test-user-agent "Mozilla/5.0 (Linux; foo) Netscape6/6.1" true sut/linux?)
    (test-user-agent "Mozilla/5.0 (linux; foo) Netscape6/6.1" false sut/linux?)
    (test-user-agent "Mozilla/5.0 (LINUX; foo) Netscape6/6.1" false sut/linux?)
    (test-user-agent "Mozilla/5.0 (linux; Linux) Netscape6/6.1" false sut/linux?))

  (context "detects iOS"
    (test-user-agent "Mozilla/5.0 (Macintosh; foo) Netscape6/6.1" false sut/ios?)
    (test-user-agent "Mozilla/5.0 (iPhone; foo) Netscape6/6.1" true sut/ios?)
    (test-user-agent "Mozilla/5.0 (iPad; foo) Netscape6/6.1" true sut/ios?)
    (test-user-agent "Mozilla/5.0 (iPod; foo) Netscape6/6.1" true sut/ios?)
    (test-user-agent "Mozilla/5.0 (iphone; foo) Netscape6/6.1" false sut/ios?)
    (test-user-agent "Mozilla/5.0 (IPhone; foo) Netscape6/6.1" false sut/ios?)
    (test-user-agent "Mozilla/5.0 (iP; iPhone) Netscape6/6.1" false sut/ios?))

  (context "detects Android"
    (test-user-agent "Mozilla/5.0 (iPhone; foo) Netscape6/6.1" false sut/android?)
    (test-user-agent "Mozilla/5.0 (Android; foo) Netscape6/6.1" true sut/android?)
    (test-user-agent "Mozilla/5.0 (android; foo) Netscape6/6.1" false sut/android?)
    (test-user-agent "Mozilla/5.0 (ANDROID; foo) Netscape6/6.1" false sut/android?)
    (test-user-agent "Mozilla/5.0 (android; Android) Netscape6/6.1" false sut/android?))

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
