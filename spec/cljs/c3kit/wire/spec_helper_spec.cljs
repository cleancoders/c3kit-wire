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
    {:on-key-down  #(swap! ratom assoc :key-down (wjs/e-key-code %))
     :on-key-up    #(swap! ratom assoc :key-up (wjs/e-key-code %))
     :on-key-press #(swap! ratom assoc :key-press (wjs/e-key-code %))}]])

(defn key-press-should= [key-press key-code key]
  (let [press (sut/keypresses key-press)]
    (should= key-code (.-keyCode press))
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