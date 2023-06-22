(ns c3kit.wire.spec-helper-spec
  (:require-macros
    [c3kit.wire.spec-helperc :refer [should-select should-not-select should-have-invoked-ws]]
    [speclj.core :refer [describe context it should-not-be-nil should-be-nil should= should-not
                         should-not= should-have-invoked after before before-all with-stubs with around
                         stub should-contain should-not-contain should]]
    )
  (:require
    [c3kit.wire.js :as wjs]
    [c3kit.wire.spec-helper :as sut]
    [reagent.core :as reagent]
    ))

(def ratom (reagent/atom {}))

(defn content []
  [:div
   [:input#-text-input
    {:on-key-down  #(swap! ratom assoc :key-down (wjs/e-key-code %))
     :on-key-up    #(swap! ratom assoc :key-up (wjs/e-key-code %))
     :on-key-press #(swap! ratom assoc :key-press (wjs/e-key-code %))}]])

(defn key-press-should= [key-press key-code key]
  (let [press (sut/keypresses key-press)]
    (should= key-code (.-keyCode press))
    (should= key (.-key press))))

(describe "Spec Helpers"

  (context "Keyboard Events"
    (sut/with-root-dom)
    (before
      (reset! ratom {})
      (sut/render [content])
      (sut/flush))

    (it "simulates a key down event"
      (sut/key-down! "#-text-input" wjs/ESC)
      (should= wjs/ESC (:key-down @ratom)))

    (it "simulates a key up event"
      (sut/key-up! "#-text-input" wjs/ENTER)
      (should= wjs/ENTER (:key-up @ratom)))

    (it "simulates a key press event"
      (sut/key-press! "#-text-input" wjs/ENTER)
      (should= wjs/ENTER (:key-press @ratom)))

    )

  (it "key presses"
    (key-press-should= wjs/TAB 9 "Tab")
    (key-press-should= wjs/ENTER 13 "Enter")
    (key-press-should= wjs/ESC 27 "Escape")
    (key-press-should= wjs/LEFT 37 "ArrowLeft")
    (key-press-should= wjs/UP 38 "ArrowUp")
    (key-press-should= wjs/RIGHT 39 "ArrowRight")
    (key-press-should= wjs/DOWN 40 "ArrowDown")
    (key-press-should= wjs/DIGIT0 48 "Digit0")
    (key-press-should= wjs/DIGIT1 49 "Digit1")
    (key-press-should= wjs/DIGIT2 50 "Digit2")
    (key-press-should= wjs/DIGIT3 51 "Digit3")
    (key-press-should= wjs/DIGIT4 52 "Digit4")
    (key-press-should= wjs/DIGIT5 53 "Digit5")
    (key-press-should= wjs/DIGIT6 54 "Digit6")
    (key-press-should= wjs/DIGIT7 55 "Digit7")
    (key-press-should= wjs/DIGIT8 56 "Digit8")
    (key-press-should= wjs/DIGIT9 57 "Digit9"))
  )