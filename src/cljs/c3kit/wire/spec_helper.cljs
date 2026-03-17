(ns c3kit.wire.spec-helper
  (:refer-clojure :exclude [flush reset! swap!])
  (:require-macros [speclj.core :refer [after before redefs-around should-have-invoked should= stub with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.mock.manual-worker :as worker]
            [c3kit.wire.mock.memory-server :as mem-server]
            [c3kit.wire.mock.memory-storage :as mem-store]
            [c3kit.wire.mock.memory-websocket :as mem-ws]
            [c3kit.wire.mock.server :as server]
            [c3kit.wire.websocket :as ws]
            [cljs.pprint :as pp]
            [clojure.string :as str]
            [reagent.core :as reagent]
            [reagent.dom.client :as domc]
            [reagent.impl.batching :as batch]
            [speclj.core]
            [speclj.stub :as stub]))

(def pprint pp/pprint)

; React 18 requires this flag for act() to work properly in tests.
(set! js/IS_REACT_ACT_ENVIRONMENT true)

(def ^:private render-roots (atom {}))

(defn- unmount-render-roots []
  (doseq [[_ root] @render-roots]
    (domc/unmount root))
  (clojure.core/reset! render-roots {}))

(defn unmount
  "Unmounts the React root for the given container (or #root by default).
   Use this instead of reagent.dom/unmount-component-at-node for React 18."
  ([] (unmount (select "#root")))
  ([container]
   (when-let [root (get @render-roots container)]
     (act #(domc/unmount root))
     (clojure.core/swap! render-roots dissoc container))))

(defn reset-dom! [content]
  (let [body (.-body js/document)]
    (unmount-render-roots)
    (set! (.-innerHTML body) content)))

(defn with-clean-dom
  ([] (with-clean-dom []))
  ([content]
   (list
    (before (reset-dom! content))
    (after (reset-dom! content)))))

(defn with-root-dom [] (with-clean-dom "<div id='root'/>"))

(defn select
  ([selector] (select js/document selector))
  ([root selector]
   (assert root (str "select: can't select inside nil nodes. " selector))
   (assert (string? selector) (str "select: selector must be a string!: " selector))
   (wjs/query-selector root selector)))

(defn select-all
  ([sel] (select-all js/document sel))
  ([root selector]
   (assert root (str "select-all: can't select inside nil nodes. " selector))
   (assert (string? selector) (str "select-all: selector must be a string!: " selector))
   (let [results (wjs/query-selector-all root selector)
         slice   #(js-invoke js/Array.prototype.slice "call" %)]
     (into [] (slice results)))))

(defn select-map
  ([f selector] (map f (select-all selector)))
  ([f root selector] (map f (select-all root selector))))

(defn count-all
  ([selector] (count (select-all selector)))
  ([root selector] (count (select-all root selector))))

(defn act
  "Wraps React.act to flush effects and state updates synchronously in tests."
  [f]
  (.act js/React f))

(defn render
  "Use me to render components for testing.  Using reagent/render directly may work, but is not as good."
  ([component] (render component (select "#root")))
  ([component container]
   (let [react-root (or (get @render-roots container)
                        (let [root (domc/create-root container)]
                          (clojure.core/swap! render-roots assoc container root)
                          root))]
     (try
       (act (fn []
              (.render react-root (reagent/as-element component))
              (reagent/flush)))
       (act (fn []
              (reagent/flush)
              (batch/flush-after-render)))
       (catch :default e (throw (ex-info "Render Error" {:message e})))))))

(defn flush []
  (act (fn [] (reagent/flush)))
  (act (fn []
         (reagent/flush)
         (batch/flush-after-render))))

(defn reset!
  "reset! wrapped in act and flush for React 18 test compatibility."
  [atom val]
  (act (fn []
         (clojure.core/reset! atom val)
         (reagent/flush)))
  (act (fn []
         (reagent/flush)
         (batch/flush-after-render))))

(defn swap!
  "swap! wrapped in act and flush for React 18 test compatibility."
  [atom f & args]
  (act (fn []
         (apply clojure.core/swap! atom f args)
         (reagent/flush)))
  (act (fn []
         (reagent/flush)
         (batch/flush-after-render))))

(defn- resolve-node
  ([action thing]
   (if (string? thing)
     (if-let [node (select thing)]
       node
       (throw (ex-info (str action " - can't find node: " thing) {:action action :thing thing})))
     (if thing
       thing
       (throw (ex-info (str action " - node is nil") {:action action :thing thing})))))
  ([action root selector]
   (when (nil? root)
     (throw (ex-info (str action " - root node is nil") {:action action :root root :selector selector})))
   (if-let [node (select root selector)]
     node
     (throw (ex-info (str action " - can't find child node: " selector) {:action action :root root :selector selector})))))

(defn- assoc-key-event [m code key]
  (assoc m code (clj->js {:code code :key key})))

; https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_key_values
(def keypresses
  (->> {wjs/ESC    "Escape"
        wjs/TAB    "Tab"
        wjs/ENTER  "Enter"
        wjs/LEFT   "ArrowLeft"
        wjs/UP     "ArrowUp"
        wjs/RIGHT  "ArrowRight"
        wjs/DOWN   "ArrowDown"
        wjs/DIGIT0 "0"
        wjs/DIGIT1 "1"
        wjs/DIGIT2 "2"
        wjs/DIGIT3 "3"
        wjs/DIGIT4 "4"
        wjs/DIGIT5 "5"
        wjs/DIGIT6 "6"
        wjs/DIGIT7 "7"
        wjs/DIGIT8 "8"
        wjs/DIGIT9 "9"
        }
       (reduce-kv assoc-key-event {})))

;region Private Event Helpers

(defn- dispatch-event
  "Dispatches a DOM event on the node, wrapped in act()."
  [node event]
  (act #(.dispatchEvent node event)))

(defn- mouse-event [type opts]
  (js/MouseEvent. type (clj->js (merge {:bubbles true :cancelable true} opts))))

(defn- keyboard-event [type opts]
  (js/KeyboardEvent. type (clj->js (merge {:bubbles true :cancelable true} opts))))

(defn- focus-event [type opts]
  (js/FocusEvent. type (clj->js (merge {:bubbles false :cancelable true} opts))))

(defn- base-event [type opts]
  (js/Event. type (clj->js (merge {:bubbles true :cancelable true} opts))))

(defn- drag-event [type data-transfer opts]
  (let [init (clj->js (merge {:bubbles true :cancelable true} opts))
        event (if (exists? js/DragEvent)
                (js/DragEvent. type init)
                (js/Event. type init))]
    (js/Object.defineProperty event "dataTransfer" #js {:value (clj->js data-transfer)})
    event))

(defn- touch-event [type opts]
  (let [init (clj->js (merge {:bubbles true :cancelable true} opts))]
    (if (exists? js/TouchEvent)
      (js/TouchEvent. type init)
      (js/Event. type init))))

(defn- set-native-files! [node files]
  (js/Object.defineProperty node "files" #js {:value (clj->js files) :configurable true}))

(def ^:private key->charcode
  {"Enter" 13 "Tab" 9 "Escape" 27 "Backspace" 8 "Delete" 46
   "ArrowLeft" 37 "ArrowUp" 38 "ArrowRight" 39 "ArrowDown" 40})

(defn- dispatch-key [event-type node key-code opts]
  (let [press (get keypresses key-code (clj->js {:code key-code :key (str key-code)}))
        key-str (.-key press)
        char-code (or (key->charcode key-str)
                      (when (= 1 (count key-str)) (.charCodeAt key-str 0))
                      0)
        base {:code (.-code press) :key key-str :charCode char-code :keyCode char-code}]
    (dispatch-event node (keyboard-event event-type (merge base opts)))))

;endregion

;region Keyboard Events

(defn key-down
  ([thing key-code]
   (dispatch-key "keydown" (resolve-node :key-down thing) key-code {}))
  ([root selector key-code]
   (dispatch-key "keydown" (resolve-node :key-down root selector) key-code {}))
  ([root selector key-code opts]
   (dispatch-key "keydown" (resolve-node :key-down root selector) key-code opts)))

(defn key-down!
  ([thing key-code] (key-down thing key-code) (flush))
  ([root selector key-code] (key-down root selector key-code) (flush))
  ([root selector key-code opts] (key-down root selector key-code opts) (flush)))

(defn key-up
  ([thing key-code]
   (dispatch-key "keyup" (resolve-node :key-up thing) key-code {}))
  ([root selector key-code]
   (dispatch-key "keyup" (resolve-node :key-up root selector) key-code {}))
  ([root selector key-code opts]
   (dispatch-key "keyup" (resolve-node :key-up root selector) key-code opts)))

(defn key-up!
  ([thing key-code] (key-up thing key-code) (flush))
  ([root selector key-code] (key-up root selector key-code) (flush))
  ([root selector key-code opts] (key-up root selector key-code opts) (flush)))

(defn key-press
  ([thing key-code]
   (dispatch-key "keypress" (resolve-node :key-press thing) key-code {}))
  ([root selector key-code]
   (dispatch-key "keypress" (resolve-node :key-press root selector) key-code {}))
  ([root selector key-code opts]
   (dispatch-key "keypress" (resolve-node :key-press root selector) key-code opts)))

(defn key-press!
  ([thing key-code] (key-press thing key-code) (flush))
  ([root selector key-code] (key-press root selector key-code) (flush))
  ([root selector key-code opts] (key-press root selector key-code opts) (flush)))

;endregion

;region Touch Events

(defn touch-start
  ([thing] (touch-start thing {}))
  ([thing opts]
   (if (string? opts)
     (dispatch-event (resolve-node :touch-start thing opts) (touch-event "touchstart" {}))
     (dispatch-event (resolve-node :touch-start thing) (touch-event "touchstart" opts))))
  ([root selector opts]
   (dispatch-event (resolve-node :touch-start root selector) (touch-event "touchstart" opts))))

(defn touch-start!
  ([thing] (touch-start thing) (flush))
  ([thing opts] (touch-start thing opts) (flush))
  ([root selector opts] (touch-start root selector opts) (flush)))

(defn touch-end
  ([thing] (touch-end thing {}))
  ([thing opts]
   (if (string? opts)
     (dispatch-event (resolve-node :touch-end thing opts) (touch-event "touchend" {}))
     (dispatch-event (resolve-node :touch-end thing) (touch-event "touchend" opts))))
  ([root selector opts]
   (dispatch-event (resolve-node :touch-end root selector) (touch-event "touchend" opts))))

(defn touch-end!
  ([thing] (touch-end thing) (flush))
  ([thing opts] (touch-end thing opts) (flush))
  ([root selector opts] (touch-end root selector opts) (flush)))

;endregion

;region Mouse Events

(defn click
  ([thing] (click thing {}))
  ([thing opts]
   (if (string? opts)
     (dispatch-event (resolve-node :click thing opts) (mouse-event "click" {}))
     (dispatch-event (resolve-node :click thing) (mouse-event "click" opts))))
  ([root selector opts]
   (dispatch-event (resolve-node :click root selector) (mouse-event "click" opts))))

(defn click!
  ([thing] (click thing) (flush))
  ([thing opts] (click thing opts) (flush))
  ([root selector opts] (click root selector opts) (flush)))

(defn mouse-enter
  ([thing] (mouse-enter thing {}))
  ([thing opts]
   (if (string? opts)
     (let [node (resolve-node :mouse-enter thing opts)]
       (dispatch-event node (mouse-event "mouseover" {}))
       (dispatch-event node (mouse-event "mouseenter" {})))
     (let [node (resolve-node :mouse-enter thing)]
       (dispatch-event node (mouse-event "mouseover" opts))
       (dispatch-event node (mouse-event "mouseenter" opts)))))
  ([root selector opts]
   (let [node (resolve-node :mouse-enter root selector)]
     (dispatch-event node (mouse-event "mouseover" opts))
     (dispatch-event node (mouse-event "mouseenter" opts)))))

(defn mouse-enter!
  ([thing] (mouse-enter thing) (flush))
  ([thing opts] (mouse-enter thing opts) (flush))
  ([root selector opts] (mouse-enter root selector opts) (flush)))

(defn mouse-up
  ([thing] (mouse-up thing {}))
  ([thing opts]
   (if (string? opts)
     (dispatch-event (resolve-node :mouse-up thing opts) (mouse-event "mouseup" {}))
     (dispatch-event (resolve-node :mouse-up thing) (mouse-event "mouseup" opts))))
  ([root selector opts]
   (dispatch-event (resolve-node :mouse-up root selector) (mouse-event "mouseup" opts))))

(defn mouse-up!
  ([thing] (mouse-up thing) (flush))
  ([thing opts] (mouse-up thing opts) (flush))
  ([root selector opts] (mouse-up root selector opts) (flush)))

(defn mouse-move
  ([thing] (mouse-move thing {}))
  ([thing opts]
   (if (string? opts)
     (dispatch-event (resolve-node :mouse-move thing opts) (mouse-event "mousemove" {}))
     (dispatch-event (resolve-node :mouse-move thing) (mouse-event "mousemove" opts))))
  ([root selector opts]
   (dispatch-event (resolve-node :mouse-move root selector) (mouse-event "mousemove" opts))))

(defn mouse-move!
  ([thing] (mouse-move thing) (flush))
  ([thing opts] (mouse-move thing opts) (flush))
  ([root selector opts] (mouse-move root selector opts) (flush)))

(defn mouse-down
  ([thing] (mouse-down thing {}))
  ([thing opts]
   (if (string? opts)
     (dispatch-event (resolve-node :mouse-down thing opts) (mouse-event "mousedown" {:button 0 :buttons 1}))
     (let [opts (if (number? opts) {:button opts} opts)]
       (dispatch-event (resolve-node :mouse-down thing) (mouse-event "mousedown" (merge {:button 0 :buttons 1} opts))))))
  ([root selector opts]
   (let [opts (if (number? opts) {:button opts} opts)]
     (dispatch-event (resolve-node :mouse-down root selector) (mouse-event "mousedown" (merge {:button 0 :buttons 1} opts))))))

(defn mouse-down!
  ([thing] (mouse-down thing) (flush))
  ([thing opts] (mouse-down thing opts) (flush))
  ([root selector opts] (mouse-down root selector opts) (flush)))

(defn mouse-leave
  ([thing] (mouse-leave thing {}))
  ([thing opts]
   (if (string? opts)
     (let [node (resolve-node :mouse-leave thing opts)]
       (dispatch-event node (mouse-event "mouseout" {}))
       (dispatch-event node (mouse-event "mouseleave" {})))
     (let [node (resolve-node :mouse-leave thing)]
       (dispatch-event node (mouse-event "mouseout" opts))
       (dispatch-event node (mouse-event "mouseleave" opts)))))
  ([root selector opts]
   (let [node (resolve-node :mouse-leave root selector)]
     (dispatch-event node (mouse-event "mouseout" opts))
     (dispatch-event node (mouse-event "mouseleave" opts)))))

(defn mouse-leave!
  ([thing] (mouse-leave thing) (flush))
  ([thing opts] (mouse-leave thing opts) (flush))
  ([root selector opts] (mouse-leave root selector opts) (flush)))

;endregion

;region Drag Events

(defn drag
  ([thing data-transfer]
   (dispatch-event (resolve-node :drag thing) (drag-event "drag" data-transfer {})))
  ([root selector data-transfer]
   (dispatch-event (resolve-node :drag root selector) (drag-event "drag" data-transfer {})))
  ([root selector data-transfer opts]
   (dispatch-event (resolve-node :drag root selector) (drag-event "drag" data-transfer opts))))

(defn drag!
  ([thing data-transfer] (drag thing data-transfer) (flush))
  ([root selector data-transfer] (drag root selector data-transfer) (flush))
  ([root selector data-transfer opts] (drag root selector data-transfer opts) (flush)))

(defn drag-start
  ([thing data-transfer]
   (dispatch-event (resolve-node :drag-start thing) (drag-event "dragstart" data-transfer {})))
  ([root selector data-transfer]
   (dispatch-event (resolve-node :drag-start root selector) (drag-event "dragstart" data-transfer {})))
  ([root selector data-transfer opts]
   (dispatch-event (resolve-node :drag-start root selector) (drag-event "dragstart" data-transfer opts))))

(defn drag-start!
  ([thing data-transfer] (drag-start thing data-transfer) (flush))
  ([root selector data-transfer] (drag-start root selector data-transfer) (flush))
  ([root selector data-transfer opts] (drag-start root selector data-transfer opts) (flush)))

(defn drag-end
  ([thing data-transfer]
   (dispatch-event (resolve-node :drag-end thing) (drag-event "dragend" data-transfer {})))
  ([root selector data-transfer]
   (dispatch-event (resolve-node :drag-end root selector) (drag-event "dragend" data-transfer {})))
  ([root selector data-transfer opts]
   (dispatch-event (resolve-node :drag-end root selector) (drag-event "dragend" data-transfer opts))))

(defn drag-end!
  ([thing data-transfer] (drag-end thing data-transfer) (flush))
  ([root selector data-transfer] (drag-end root selector data-transfer) (flush))
  ([root selector data-transfer opts] (drag-end root selector data-transfer opts) (flush)))

(defn drag-enter
  ([thing data-transfer]
   (dispatch-event (resolve-node :drag-enter thing) (drag-event "dragenter" data-transfer {})))
  ([root selector data-transfer]
   (dispatch-event (resolve-node :drag-enter root selector) (drag-event "dragenter" data-transfer {})))
  ([root selector data-transfer opts]
   (dispatch-event (resolve-node :drag-enter root selector) (drag-event "dragenter" data-transfer opts))))

(defn drag-enter!
  ([thing data-transfer] (drag-enter thing data-transfer) (flush))
  ([root selector data-transfer] (drag-enter root selector data-transfer) (flush))
  ([root selector data-transfer opts] (drag-enter root selector data-transfer opts) (flush)))

(defn drag-leave
  ([thing data-transfer]
   (dispatch-event (resolve-node :drag-leave thing) (drag-event "dragleave" data-transfer {})))
  ([root selector data-transfer]
   (dispatch-event (resolve-node :drag-leave root selector) (drag-event "dragleave" data-transfer {})))
  ([root selector data-transfer opts]
   (dispatch-event (resolve-node :drag-leave root selector) (drag-event "dragleave" data-transfer opts))))

(defn drag-leave!
  ([thing data-transfer] (drag-leave thing data-transfer) (flush))
  ([root selector data-transfer] (drag-leave root selector data-transfer) (flush))
  ([root selector data-transfer opts] (drag-leave root selector data-transfer opts) (flush)))

(defn drag-over
  ([thing data-transfer]
   (dispatch-event (resolve-node :drag-over thing) (drag-event "dragover" data-transfer {})))
  ([root selector data-transfer]
   (dispatch-event (resolve-node :drag-over root selector) (drag-event "dragover" data-transfer {})))
  ([root selector data-transfer opts]
   (dispatch-event (resolve-node :drag-over root selector) (drag-event "dragover" data-transfer opts))))

(defn drag-over!
  ([thing data-transfer] (drag-over thing data-transfer) (flush))
  ([root selector data-transfer] (drag-over root selector data-transfer) (flush))
  ([root selector data-transfer opts] (drag-over root selector data-transfer opts) (flush)))

(defn on-drop
  ([thing data-transfer]
   (dispatch-event (resolve-node :on-drop thing) (drag-event "drop" data-transfer {})))
  ([root selector data-transfer]
   (dispatch-event (resolve-node :on-drop root selector) (drag-event "drop" data-transfer {})))
  ([root selector data-transfer opts]
   (dispatch-event (resolve-node :on-drop root selector) (drag-event "drop" data-transfer opts))))

(defn on-drop!
  ([thing data-transfer] (on-drop thing data-transfer) (flush))
  ([root selector data-transfer] (on-drop root selector data-transfer) (flush))
  ([root selector data-transfer opts] (on-drop root selector data-transfer opts) (flush)))

;endregion

;region Focus Events

(defn focus
  ([thing] (dispatch-event (resolve-node :focus thing) (focus-event "focus" {})))
  ([thing opts]
   (if (string? opts)
     (dispatch-event (resolve-node :focus thing opts) (focus-event "focus" {}))
     (dispatch-event (resolve-node :focus thing) (focus-event "focus" opts))))
  ([root selector opts]
   (dispatch-event (resolve-node :focus root selector) (focus-event "focus" opts))))

(defn focus!
  ([thing] (focus thing) (flush))
  ([thing opts] (focus thing opts) (flush))
  ([root selector opts] (focus root selector opts) (flush)))

(defn blur
  ([thing] (dispatch-event (resolve-node :blur thing) (focus-event "blur" {})))
  ([thing opts]
   (if (string? opts)
     (dispatch-event (resolve-node :blur thing opts) (focus-event "blur" {}))
     (dispatch-event (resolve-node :blur thing) (focus-event "blur" opts))))
  ([root selector opts]
   (dispatch-event (resolve-node :blur root selector) (focus-event "blur" opts))))

(defn blur!
  ([thing] (blur thing) (flush))
  ([thing opts] (blur thing opts) (flush))
  ([root selector opts] (blur root selector opts) (flush)))

;endregion

;region Change Events

(defn- set-native-value!
  "Sets the value using the native setter to bypass React's internal value tracker,
   ensuring React detects the change and fires onChange."
  [node value]
  (let [proto     (js/Object.getPrototypeOf node)
        descriptor (js/Object.getOwnPropertyDescriptor proto "value")]
    (when-let [setter (and descriptor (.-set descriptor))]
      (.call setter node value))))

(defn- set-native-checked!
  "Sets the checked property using the native setter to bypass React's internal tracker."
  [node value]
  (let [proto      (js/Object.getPrototypeOf node)
        descriptor (js/Object.getOwnPropertyDescriptor proto "checked")]
    (if-let [setter (and descriptor (.-set descriptor))]
      (.call setter node value)
      (set! (.-checked node) value))))

(defn- checkbox-or-radio? [node]
  (let [type (.-type node)]
    (or (= "checkbox" type) (= "radio" type))))

(defn- select-element? [node]
  (= "SELECT" (.-tagName node)))

(defn change
  ([thing]
   (let [node (resolve-node :change thing)]
     (if (checkbox-or-radio? node)
       (dispatch-event node (mouse-event "click" {}))
       (dispatch-event node (base-event "input" {})))))
  ([thing value]
   (let [node (resolve-node :change thing)]
     (cond
       (= "file" (.-type node))
       (do (set-native-files! node value)
           (dispatch-event node (base-event "change" {})))

       (checkbox-or-radio? node)
       (do (when (not= (boolean value) (.-checked node))
             (dispatch-event node (mouse-event "click" {}))))

       (select-element? node)
       (do (set-native-value! node value)
           (dispatch-event node (base-event "change" {})))

       :else
       (do (set-native-value! node value)
           (dispatch-event node (base-event "input" {}))))))
  ([root selector value]
   (change (resolve-node :change root selector) value)))

(defn change!
  ([thing value] (change thing value) (flush))
  ([root selector value] (change root selector value) (flush)))

(defn check-box
  ([thing value]
   (let [node (resolve-node :check-box thing)]
     (when (not= (boolean value) (.-checked node))
       (dispatch-event node (mouse-event "click" {})))))
  ([root selector value]
   (check-box (resolve-node :check-box root selector) value)))

(defn check-box!
  ([thing value] (check-box thing value) (flush))
  ([root selector value] (check-box root selector value) (flush)))

;endregion

(defn text!
  "Throws exception if the node doesn't exist."
  ([thing]
   (.-innerText (resolve-node :text thing)))
  ([root selector]
   (.-innerText (resolve-node :text root selector))))

(defn text
  "Return nil if the node doesn't exist."
  ([] (wjs/node-text (wjs/doc-body)))
  ([selector-or-elem]
   (cond
     (string? selector-or-elem) (some-> (select selector-or-elem) wjs/node-text)
     (nil? selector-or-elem) nil
     :else (wjs/node-text selector-or-elem)))
  ([root selector]
   (some-> (select root selector) wjs/node-text)))


(defn html!
  "Throws exception if the node doesn't exist."
  ([thing]
   (.-innerHTML (resolve-node :html thing)))
  ([root selector]
   (.-innerHTML (resolve-node :html root selector))))

(defn html
  "Return nil if the node doesn't exist."
  ([] (.-innerHTML (.-body js/document)))
  ([selector-or-elem]
   (cond
     (string? selector-or-elem) (when-let [e (select selector-or-elem)] (.-innerHTML e))
     (nil? selector-or-elem) nil
     :else (.-innerHTML selector-or-elem)))
  ([root selector]
   (when-let [e (select root selector)]
     (.-innerHTML e))))

(defn class-name
  ([thing]
   (.-className (resolve-node :class-name thing)))
  ([root selector]
   (.-className (resolve-node :class-name root selector))))

(defn tag-name
  ([thing]
   (.-tagName (resolve-node :tag-name thing)))
  ([root selector]
   (.-tagName (resolve-node :tag-name root selector))))

(defn href
  ([thing]
   (.-href (resolve-node :href thing)))
  ([root selector]
   (.-href (resolve-node :href root selector))))

(defn id
  ([thing]
   (.-id (resolve-node :id thing)))
  ([root selector]
   (.-id (resolve-node :id root selector))))

(defn value
  ([thing]
   (.-value (resolve-node :value thing)))
  ([root selector]
   (.-value (resolve-node :value root selector))))

(defn placeholder
  ([thing]
   (.-placeholder (resolve-node :placeholder thing)))
  ([root selector]
   (.-placeholder (resolve-node :placeholder root selector))))

(defn checked?
  ([thing]
   (.-checked (resolve-node :checked? thing)))
  ([root selector]
   (.-checked (resolve-node :checked? root selector))))

(defn disabled?
  ([thing]
   (.-disabled (resolve-node :disabled? thing)))
  ([root selector]
   (.-disabled (resolve-node :disabled? root selector))))

(defn readonly?
  ([thing]
   (.-readOnly (resolve-node :readonly? thing)))
  ([root selector]
   (.-readOnly (resolve-node :readonly? root selector))))

(defn src
  ([thing]
   (.-src (resolve-node :src thing)))
  ([root selector]
   (.-src (resolve-node :src root selector))))

(defn alt
  ([thing]
   (.-alt (resolve-node :alt thing)))
  ([root selector]
   (.-alt (resolve-node :alt root selector))))

(defn style
  ([thing]
   (.-style (resolve-node :id thing)))
  ([root selector]
   (.-style (resolve-node :id root selector))))

(defn print-html [] (println "HTML: " (html)))

(defn print-error [e file line col]
  (println "********* JS ERROR *********")
  (println "\t" e)
  (println "\t" (str/join ":" [file line col]))
  (println "****************************"))
(defn print-js-errors [] (set! (.-onerror js/window) print-error))

(defn stub-ajax []
  (redefs-around [ajax/post! (stub :ajax/post!)
                  ajax/get! (stub :ajax/get!)]))

(defn last-ajax-post-url [] (when-let [args (stub/last-invocation-of :ajax/post!)] (first args)))
(defn last-ajax-get-url [] (when-let [args (stub/last-invocation-of :ajax/get!)] (first args)))

(defn last-ajax-post-params [] (when-let [args (stub/last-invocation-of :ajax/post!)] (second args)))
(defn last-ajax-get-params [] (when-let [args (stub/last-invocation-of :ajax/get!)] (second args)))

(defn last-ajax-post-handler [] (when-let [args (stub/last-invocation-of :ajax/post!)] (nth args 2)))
(defn last-ajax-get-handler [] (when-let [args (stub/last-invocation-of :ajax/get!)] (nth args 2)))

(defn last-ajax-post-options [] (when-let [args (stub/last-invocation-of :ajax/post!)] (ccc/->options (drop 3 args))))
(defn last-ajax-get-options [] (when-let [args (stub/last-invocation-of :ajax/get!)] (ccc/->options (drop 3 args))))

(defn invoke-last-ajax-post-handler [payload] (some-> (last-ajax-post-handler) (ccc/invoke payload)))
(defn invoke-last-ajax-get-handler [payload] (some-> (last-ajax-get-handler) (ccc/invoke payload)))

(defn stub-ws [] (redefs-around [ws/call! (stub :ws/call!)]))
(defn last-ws-call-id [] (when-let [args (stub/last-invocation-of :ws/call!)] (first args)))
(defn last-ws-call-params [] (when-let [args (stub/last-invocation-of :ws/call!)] (second args)))
(defn last-ws-call-handler [] (when-let [args (stub/last-invocation-of :ws/call!)] (nth args 2)))
(defn last-ws-call-options [] (when-let [args (stub/last-invocation-of :ws/call!)] (ccc/->options (drop 3 args))))
(defn invoke-last-ws-call-handler [payload] (some-> (last-ws-call-handler) (ccc/invoke payload)))

;region Mocks

(defn with-websocket-impl [constructor]
  (let [js-websocket js/WebSocket]
    (list
     (before (set! js/WebSocket constructor)
             (clojure.core/reset! server/impl (mem-server/->MemServer)))
     (after (set! js/WebSocket js-websocket)
            (clojure.core/reset! server/impl nil)))))

(defn with-memory-websockets []
  (with-websocket-impl mem-ws/->MemSocket))

(defn stub-performance-now [time]
  (before (ccc/oset js/performance "now" (fn [] time))))

(defn- memory-storage-storage [js-store]
  (before
   (let [store (mem-store/->MemStorage)]
     (doseq [attr ["getItem" "setItem" "removeItem" "clear"]]
       (ccc/oset js-store attr (ccc/oget store attr))))))

(defn with-memory-local-storage []
  (memory-storage-storage js/localStorage))

(defn with-memory-session-storage []
  (memory-storage-storage js/sessionStorage))

(defn with-manual-worker []
  (before (worker/clear!)
          (set! js/setTimeout worker/set-timeout)
          (set! js/setInterval worker/set-interval)))

;endregion
