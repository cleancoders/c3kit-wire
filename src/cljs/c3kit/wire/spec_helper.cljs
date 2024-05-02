(ns c3kit.wire.spec-helper
  (:refer-clojure :exclude [flush])
  (:require-macros [speclj.core :refer [after before redefs-around should-have-invoked should= stub with-stubs]])
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.websocket :as ws]
            [cljs.pprint :as pp]
            [cljsjs.react.dom.test-utils] ;; Brings in js/ReactTestUtils
            [clojure.string :as str]
            [reagent.core :as reagent]
            [reagent.dom :as dom]
            [speclj.core]
            [speclj.stub :as stub]
            ))

(log/warn!)

(def pprint pp/pprint)

(def ^:private render-roots (atom []))

(defn- unmount-render-roots []
  (doseq [root @render-roots]
    (dom/unmount-component-at-node root))
  (reset! render-roots []))

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
   (.querySelector root selector)))

(defn select-all
  ([sel] (select-all js/document sel))
  ([root selector]
   (assert root (str "select-all: can't select inside nil nodes. " selector))
   (assert (string? selector) (str "select-all: selector must be a string!: " selector))
   (let [results (.querySelectorAll root selector)
         slice   #(.call js/Array.prototype.slice %)]
     (into [] (slice results)))))

(defn select-map
  ([f selector] (map f (select-all selector)))
  ([f root selector] (map f (select-all root selector))))

(defn count-all
  ([selector] (count (select-all selector)))
  ([root selector] (count (select-all root selector))))

(defn render
  "Use me to render components for testing.  Using reagent/render directly may work, but is not as good."
  ([component] (render component (select "#root")))
  ([component root]
   (swap! render-roots conj root)

   ;; TODO - MDM: This should be removed.
   (set! (.-Slider js/window) (clj->js {:default (fn [] nil)}))
   (set! js/jwplayer (fn [elem_id] (clj->js {:setup  ccc/noop
                                             :remove ccc/noop})))
   (try
     (dom/render component root)
     (catch :default e (throw (ex-info "Render Error" {:message e}))))))

(defn flush [] (reagent/flush))

(def simulator (.-Simulate js/ReactTestUtils))

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

(defn simulate
  ([event-name thing event-data]
   (let [node     (resolve-node :simulate thing)
         event-fn (wjs/o-get simulator event-name)]
     (when-not event-fn (throw (ex-info (str "simulate - event doesn't exist: " event-name) {:thing thing :event-name event-name :event-data event-data})))
     (event-fn node (clj->js event-data))))
  ([event-name root selector event-data]
   (simulate event-name (resolve-node root selector) event-data)))

(defn simulate!
  ([event-name thing event-data] (simulate event-name thing event-data) (flush))
  ([event-name root selector event-data] (simulate event-name root selector event-data) (flush)))

(defn key-down
  ([thing key-code]
   ((.-keyDown simulator)
    (resolve-node :key-down thing)
    (get keypresses key-code (clj->js {:code key-code :key (str key-code)}))))
  ([root selector key-code]
   (key-down (resolve-node :key-down root selector) key-code)))

(defn key-down!
  ([thing key-code] (key-down thing key-code) (flush))
  ([root selector key-code] (key-down root selector key-code) (flush)))

(defn key-up
  ([thing key]
   ((.-keyUp simulator) (resolve-node :key-up thing) (get keypresses key)))
  ([root selector key]
   (key-up (resolve-node :key-up root selector) key)))

(defn key-up!
  ([thing key] (key-up thing key) (flush))
  ([root selector key] (key-up root selector key) (flush)))

(defn key-press
  ([thing key]
   ((.-keyPress simulator) (resolve-node :key-press thing) (get keypresses key)))
  ([root selector key]
   (key-press (resolve-node :key-press root selector) key)))

(defn key-press!
  ([thing key] (key-press thing key) (flush))
  ([root selector key] (key-press root selector key) (flush)))

(defn touch-end
  ([thing]
   ((.-touchend simulator) (resolve-node :touchend thing)))
  ([root selector]
   (touch-end (resolve-node :touchend root selector))))

(defn touch-end!
  ([thing] (touch-end thing) (flush))
  ([root selector] (touch-end root selector) (flush)))

(defn touch-start
  ([thing]
   ((.-touchstart simulator) (resolve-node :touchstart thing)))
  ([root selector]
   (touch-start (resolve-node :touchstart root selector))))

(defn touch-start!
  ([thing] (touch-start thing) (flush))
  ([root selector] (touch-start root selector) (flush)))

(defn click
  ([thing]
   ((.-click simulator) (resolve-node :click thing)))
  ([root selector]
   (click (resolve-node :click root selector))))

(defn click!
  ([thing] (click thing) (flush))
  ([root selector] (click root selector) (flush)))

(defn mouse-enter
  ([thing]
   ((.-mouseEnter simulator) (resolve-node :mouse-enter thing)))
  ([root selector]
   (mouse-enter (resolve-node :mouse-enter root selector))))

(defn mouse-enter!
  ([thing] (mouse-enter thing) (flush))
  ([root selector] (mouse-enter root selector) (flush)))

(defn mouse-up
  ([thing]
   ((.-mouseUp simulator) (resolve-node :mouse-up thing)))
  ([root selector]
   (mouse-up (resolve-node :mouse-up root selector))))

(defn mouse-up!
  ([thing] (mouse-up thing) (flush))
  ([root selector] (mouse-up root selector) (flush)))

(defn mouse-move
  ([thing]
   ((.-mouseMove simulator) (resolve-node :mouse-move thing)))
  ([root selector]
   (mouse-move (resolve-node :mouse-move root selector))))

(defn mouse-move!
  ([thing] (mouse-move thing) (flush))
  ([root selector] (mouse-move root selector) (flush)))

(defn mouse-down
  ([thing] (mouse-down thing 0))
  ([thing button]
   (let [node (resolve-node :mouse-down thing)]
     ((.-mouseDown simulator) node (clj->js {:button button :buttons 1}))))
  ([root button selector]
   (mouse-down (resolve-node :mouse-down root selector) button)))

(defn mouse-down!
  ([thing] (mouse-down thing) (flush))
  ([root selector] (mouse-down root selector) (flush)))

(defn mouse-leave
  ([thing]
   ((.-mouseLeave simulator) (resolve-node :mouse-leave thing)))
  ([root selector]
   (mouse-leave (resolve-node :mouse-leave root selector))))

(defn mouse-leave!
  ([thing] (mouse-leave thing) (flush))
  ([root selector] (mouse-leave root selector) (flush)))

(defn drag
  ([thing data-transfer]
   ((.-drag simulator) (resolve-node :drag thing) (clj->js {:dataTransfer data-transfer})))
  ([root selector data-transfer]
   (drag (resolve-node :drag root selector) data-transfer)))

(defn drag!
  ([thing data-transfer] (drag thing data-transfer) (flush))
  ([root selector data-transfer] (drag root selector data-transfer) (flush)))

(defn drag-start
  ([thing data-transfer]
   ((.-dragStart simulator) (resolve-node :drag-start thing) (clj->js {:dataTransfer data-transfer})))
  ([root selector data-transfer]
   (drag-start (resolve-node :drag-start root selector) data-transfer)))

(defn drag-start!
  ([thing data-transfer] (drag-start thing data-transfer) (flush))
  ([root selector data-transfer] (drag-start root selector data-transfer) (flush)))

(defn drag-enter
  ([thing data-transfer]
   ((.-dragEnter simulator) (resolve-node :drag-enter thing) (clj->js {:dataTransfer data-transfer})))
  ([root selector data-transfer]
   (drag-enter (resolve-node :drag-enter root selector) data-transfer)))

(defn drag-enter!
  ([thing data-transfer] (drag-enter thing data-transfer) (flush))
  ([root selector data-transfer] (drag-enter root selector data-transfer) (flush)))

(defn drag-leave
  ([thing data-transfer]
   ((.-dragLeave simulator) (resolve-node :drag-leave thing) (clj->js {:dataTransfer data-transfer})))
  ([root selector data-transfer]
   (drag-leave (resolve-node :drag-leave root selector) data-transfer)))

(defn drag-leave!
  ([thing data-transfer] (drag-leave thing data-transfer) (flush))
  ([root selector data-transfer] (drag-leave root selector data-transfer) (flush)))

(defn drag-end
  ([thing data-transfer]
   ((.-dragEnd simulator) (resolve-node :drag-end thing) (clj->js {:dataTransfer data-transfer})))
  ([root selector data-transfer]
   (drag-end (resolve-node :drag-end root selector) data-transfer)))

(defn drag-end!
  ([thing data-transfer] (drag-end thing data-transfer) (flush))
  ([root selector data-transfer] (drag-end root selector data-transfer) (flush)))

(defn drag-over
  ([thing data-transfer]
   ((.-dragOver simulator) (resolve-node :drag-over thing) (clj->js {:dataTransfer data-transfer})))
  ([root selector data-transfer]
   (drag-over (resolve-node :drag-over root selector) data-transfer)))

(defn drag-over!
  ([thing data-transfer] (drag-over thing data-transfer) (flush))
  ([root selector data-transfer] (drag-over root selector data-transfer) (flush)))

(defn on-drop
  ([thing data-transfer]
   ((.-drop simulator) (resolve-node :on-drop thing) (clj->js {:dataTransfer data-transfer})))
  ([root selector data-transfer]
   (on-drop (resolve-node :on-drop root selector) data-transfer)))

(defn on-drop!
  ([thing data-transfer] (on-drop thing data-transfer) (flush))
  ([root selector data-transfer] (on-drop root selector data-transfer) (flush)))

(defn focus
  ([thing] ((.-focus simulator) (resolve-node :focus thing)))
  ([root selector] (focus (resolve-node :focus root selector))))

(defn focus!
  ([thing] (focus thing) (flush))
  ([root selector] (focus root selector) (flush)))

(defn blur
  ([thing] ((.-blur simulator) (resolve-node :blur thing)))
  ([root selector] (blur (resolve-node :blur root selector))))

(defn blur!
  ([thing] (blur thing) (flush))
  ([root selector] (blur root selector) (flush)))

(defn change
  ([thing]
   ((.-change simulator) (resolve-node :change thing) {:target thing}))
  ([thing value]
   (let [node (resolve-node :change thing)]
     (if (= "file" (.-type node))
       ((.-change simulator) node (clj->js {:target {:files value}}))
       (do (set! (.-value node) value)
           (change node)))))
  ([root selector value]
   (change (resolve-node :change root selector) value)))

(defn change!
  ([thing value] (change thing value) (flush))
  ([root selector value] (change root selector value) (flush)))

(defn check-box
  ([thing value]
   (let [node (resolve-node :check-box thing)]
     (set! (.-checked node) value)
     (change node)))
  ([root selector value]
   (check-box (resolve-node :check-box root selector) value)))

(defn check-box!
  ([thing value] (check-box thing value) (flush))
  ([root selector value] (check-box root selector value) (flush)))

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
                  ajax/get!  (stub :ajax/get!)]))

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
