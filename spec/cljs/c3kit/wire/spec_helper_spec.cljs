(ns c3kit.wire.spec-helper-spec
  (:require-macros
    [speclj.core :refer [after around before before-all context describe it
                         redefs-around should should-be-nil should-contain should-have-invoked should-not should-not-be-nil should-not-contain should-not-throw
                         should-not= should= stub with with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.spec-helper :as sut]
            [c3kit.wire.websocket :as ws]
            [reagent.core :as reagent]
            [speclj.stub :as stub]))

(def ratom (reagent/atom {}))

(defn content []
  [:div
   [:input#-text-input
    {:on-key-down  #(swap! ratom assoc :key-down (wjs/e-code %))
     :on-key-up    #(swap! ratom assoc :key-up (wjs/e-code %))
     :on-key-press #(swap! ratom assoc :key-press (wjs/e-code %))}]])

(defn key-press-should= [key-press code key]
  (let [press (sut/keypresses key-press)]
    (should= code (.-code press))
    (should= key (.-key press))))

(defn should-invoke-drag-event [simulate! attribute]
  (with-redefs [ccc/noop (stub :noop)]
    (sut/render [:div.container {attribute ccc/noop}])
    (simulate! ".container" {:foo :bar})
    (should-have-invoked :noop)
    (let [[event] (stub/last-invocation-of :noop)]
      (should= {"foo" "bar"} (js->clj (.-dataTransfer event))))))

(describe "Spec Helpers"
  (with-stubs)
  (sut/stub-ajax)
  (sut/stub-ws)
  (sut/with-root-dom)

  (context "Keyboard Events"
    (before (reset! ratom {})
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

  (context "changes"
    (it "text"
      (sut/render [:input {:type :text}])
      (sut/change! "input" "blah")
      (should= "blah" (sut/value "input")))

    (it "file"
      (sut/render [:input {:type :file :on-change (stub :on-change)}])
      (sut/change! "input" [{:name "kittens.png" :type "png" :size 123}])
      (let [[event] (stub/last-invocation-of :on-change)
            file (-> event .-target .-files first js->clj)]
        (should= {"name" "kittens.png" "type" "png" "size" 123} file)))
    )

  (context "drag events"
    (it "drag" (should-invoke-drag-event sut/drag! :on-drag))
    (it "drag end" (should-invoke-drag-event sut/drag-end! :on-drag-end))
    (it "drag enter" (should-invoke-drag-event sut/drag-enter! :on-drag-enter))
    (it "drag leave" (should-invoke-drag-event sut/drag-leave! :on-drag-leave))
    (it "drag over" (should-invoke-drag-event sut/drag-over! :on-drag-over))
    (it "drag start" (should-invoke-drag-event sut/drag-start! :on-drag-start))
    (it "drop" (should-invoke-drag-event sut/on-drop! :on-drop)))

  (it "key presses"
    (key-press-should= wjs/TAB "Tab" "Tab")
    (key-press-should= wjs/ENTER "Enter" "Enter")
    (key-press-should= wjs/ESC "Escape" "Escape")
    (key-press-should= wjs/LEFT "ArrowLeft" "ArrowLeft")
    (key-press-should= wjs/UP "ArrowUp" "ArrowUp")
    (key-press-should= wjs/RIGHT "ArrowRight" "ArrowRight")
    (key-press-should= wjs/DOWN "ArrowDown" "ArrowDown")
    (key-press-should= wjs/DIGIT0 "Digit0" "0")
    (key-press-should= wjs/DIGIT1 "Digit1" "1")
    (key-press-should= wjs/DIGIT2 "Digit2" "2")
    (key-press-should= wjs/DIGIT3 "Digit3" "3")
    (key-press-should= wjs/DIGIT4 "Digit4" "4")
    (key-press-should= wjs/DIGIT5 "Digit5" "5")
    (key-press-should= wjs/DIGIT6 "Digit6" "6")
    (key-press-should= wjs/DIGIT7 "Digit7" "7")
    (key-press-should= wjs/DIGIT8 "Digit8" "8")
    (key-press-should= wjs/DIGIT9 "Digit9" "9"))

  (it "invokes last ajax POST handler"
    (should-not-throw (sut/invoke-last-ajax-post-handler :params))
    (ajax/post! "/foo" {} (stub :handler))
    (sut/invoke-last-ajax-post-handler :params)
    (should-have-invoked :handler {:with [:params]}))

  (it "invokes last ajax GET handler"
    (should-not-throw (sut/invoke-last-ajax-get-handler :params))
    (ajax/get! "/foo" {} (stub :handler))
    (sut/invoke-last-ajax-get-handler :params)
    (should-have-invoked :handler {:with [:params]}))

  (it "invokes last websocket handler"
    (should-not-throw (sut/invoke-last-ws-call-handler :params))
    (ws/call! :foo {} (stub :handler))
    (sut/invoke-last-ws-call-handler :params)
    (should-have-invoked :handler {:with [:params]}))
  )
