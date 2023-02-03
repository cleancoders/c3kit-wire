(ns c3kit.wire.js
  (:require [c3kit.apron.log :as log]
            [c3kit.apron.time :as time]
            [clojure.string :as s]
            [goog.object :as gobject])
  (:import (goog History)))

;; ----- Key Codes and Events -----

(defn e-key-code [e] (.-keyCode e))

(def BACKSPACE 8)
(def TAB 9)
(def ENTER 13)
(def SHIFT 16)
(def ESC 27)
(def SPACE 32)
(def LEFT 37)
(def UP 38)
(def RIGHT 39)
(def DOWN 40)
(def DELETE 46)
(def NUMPAD+ 107)
(def COMMA 188)

(defn- KEY? [key-code e] (= (e-key-code e) key-code))
(def BACKSPACE? (partial KEY? BACKSPACE))
(def TAB? (partial KEY? TAB))
(def ENTER? (partial KEY? ENTER))
(def SHIFT? (partial KEY? SHIFT))
(def ESC? (partial KEY? ESC))
(def SPACE? (partial KEY? SPACE))
(def LEFT? (partial KEY? LEFT))
(def UP? (partial KEY? UP))
(def RIGHT? (partial KEY? RIGHT))
(def DOWN? (partial KEY? DOWN))
(def DELETE? (partial KEY? DELETE))
(def NUMPAD+? (partial KEY? NUMPAD+))
(def COMMA? (partial KEY? COMMA))

(defn key-modifier? [e modifier]
  (try ;; test keyboard events don't seem to support this.
    (.getModifierState e modifier)
    (catch :default e false)))

(defn shift-modifier? [e] (key-modifier? e "Shift"))
(defn ctl-modifier? [e] (key-modifier? e "Control"))
(defn alt-modifier? [e] (key-modifier? e "Alt"))

;; ^^^^^ Key Codes and Events ^^^^^

(defn o-get
  ([js-obj key] (gobject/get js-obj key nil))
  ([js-obj key default] (gobject/get js-obj key default)))

(defn o-set [js-obj key value] (gobject/set js-obj key value))

;; ----- Simple js function translations -----

(defn clear-interval [interval] (js/clearInterval interval))
(defn clear-timeout [timeout] (js/clearTimeout timeout))
(defn context-2d [canvas] (.getContext canvas "2d"))
(defn doc-body
  ([] (.-body js/document))
  ([doc] (.-body doc)))
(defn doc-cookie [] (.-cookie js/document))
(defn doc-ready-state [] (.-readyState js/document))
(defn doc-ready? [] (= "complete" (doc-ready-state)))
(defn document ([] js/document) ([node] (.-ownerDocument node)))
(defn e-checked? [e] (-> e .-target .-checked))
(defn e-client-x [e] (.-clientX e))
(defn e-client-y [e] (.-clientY e))
(defn e-coordinates [e] [(.-clientX e) (.-clientY e)])
(defn e-left-click? [e] (= 0 (o-get e "button")))
(defn e-related-target [e] (.-relatedTarget e))
(defn e-right-click? [e] (= 2 (o-get e "button")))
(defn e-target [e] (.-target e))
(defn e-text [e] (-> e .-target .-value))
(defn e-type [e] (.-type e))
(defn e-wheel-click? [e] (= 1 (o-get e "button")))
(defn element-by-id [id] (.getElementById js/document id))
(defn frame-window [iframe] (.-contentWindow iframe))
(defn interval [millis f] (js/setInterval f millis))
(defn node-add-class [node class] (.add (.-classList node) class))
(defn node-append-child [node child] (.appendChild node child))
(defn node-children [node] (array-seq (.-childNodes node)))
(defn node-classes [node] (.-className node))
(defn node-clone [node deep?] (.cloneNode node deep?))
(defn node-height [node] (.-clientHeight node))
(defn node-id [node] (o-get node "id"))
(defn node-id= [node id] (o-set node "id" id))
(defn node-text [node] (.-innerText node))
(defn node-parent [node] (.-parentNode node))
(defn node-placeholder [node] (.-placeholder node))
(defn node-remove-child [node child] (.removeChild node child))
(defn node-remove-class [node class] (.remove (.-classList node) class))
(defn node-scroll-left [node] (.-scrollLeft node))
(defn node-scroll-top [node] (.-scrollTop node))
(defn node-size [node] [(.-clientWidth node) (.-clientHeight node)])
(defn node-style [node] (.-style node))
(defn node-value [node] (.-value node))
(defn node-width [node] (.-clientWidth node))
(defn page-href [] (-> js/window .-location .-href))
(defn page-pathname [] (-> js/window .-location .-pathname))
(defn page-reload! [] (.reload (.-location js/window)))
(defn page-title [] (.-title js/document))
(defn page-title= [title] (set! (.-title js/document) title))
(defn post-message [window message target-domain] (.postMessage window (clj->js message) target-domain))
(defn print-page [] (.print js/window))
(defn query-selector [selector] (.querySelector js/document selector))
(defn register-post-message-handler [handler] (.addEventListener js/window "message" handler))
(defn register-storage-handler [handler] (.addEventListener js/window "storage" handler))
(defn remove-local-storage [key] (.removeItem js/localStorage key))
(defn screen-size [] [(.-width js/screen) (.-height js/screen)])
(defn set-local-storage [key value] (.setItem js/localStorage key value))
(defn timeout [millis f] (js/setTimeout f millis))
(defn uri-encode [& stuff] (js/encodeURIComponent (apply str stuff)))
(defn window-close! [] (.close js/window))
(defn window-open [url window-name options-string] (.open js/window url window-name options-string))

;; ^^^^^ Simple js function translations ^^^^^

(defn node-bounds [node]
  (let [rect (.getBoundingClientRect node)]
    [(.-x rect) (.-y rect) (.-width rect) (.-height rect)]))

(defn cookies
  "Return a hashmap of cookie names and their values."
  []
  (into {} (comp (remove s/blank?) (map #(s/split % "="))) (s/split (doc-cookie) "; ")))

(defn resolve-node
  "Resolves a value into a DOM node.
  Possible values:
    - string id       - id of the node
    - string selector - CSS selector to find the node
    - node            - anything else is assumed to be the node"
  [thing]
  (if (string? thing)
    (or (element-by-id thing) (query-selector thing))
    thing))

(defn ancestor-where [pred node]
  (cond
    (nil? node) nil
    (pred node) node
    :else (recur pred (.-parentElement node))))

(defn ancestor? [ancestor node]
  (boolean (ancestor-where #(= ancestor %) node)))

(defn nod
  "Give an event the nod, as if saying: Good job, your work is done."
  [e]
  (.preventDefault e))

(defn nod-n-do
  "Return function to suppress browser event with nod and call the supplied function with args."
  [a-fn & args]
  (fn [e]
    (nod e)
    (apply a-fn args)))

(defn nip
  "Nip the event in the bud, before it causes any trouble."
  [e]
  (.stopPropagation e))

(defn nip-n-do
  "Return function to suppress browser event with nip and call the supplied function with args."
  [a-fn & args]
  (fn [e]
    (nip e)
    (apply a-fn args)))

(defn nix
  "Nix an event: Stop what you're doing and get the hell out."
  [e]
  (nip e)
  (nod e))

(defn nix-n-do
  "Return function to suppress browser event with nix and call the supplied function with args."
  [a-fn & args]
  (fn [e]
    (nix e)
    (apply a-fn args)))


(defn redirect!
  "Tell the browser to load the given URL, with full HTTP request/response process."
  [url]
  (set! (.-location js/window) url))


(defn e-value
  "Return the value of an event based on the target type:
     checkbox - boolean
     date - UTC instant or nil if value is not a date
     text - string"
  [e]
  (let [target (e-target e)]
    (case (e-type target)
      "checkbox" (.-checked target)
      "date" (try (time/parse :webform (.-value target)) (catch js/Error _))
      (.-value target))))

(defn focus!
  "Resolved the node and focus.
  Options: {:preventScroll true ;; default is false
            :focusVisible true}  ;; default may be false "
  ([thing] (some-> thing resolve-node (.focus)))
  ([thing options] (some-> thing resolve-node (.focus (clj->js options)))))
(defn blur! [thing] (some-> thing resolve-node (.blur)))

(defn add-listener
  ([node event listener] (add-listener node event listener nil))
  ([node event listener options]
   (if node
     (.addEventListener node event listener (when options (clj->js options)))
     (log/warn "add-listener to nil node"))))

(defn remove-listener
  ([node event listener] (remove-listener node event listener nil))
  ([node event listener options]
   (if node
     (.removeEventListener node event listener (when options (clj->js options)))
     (log/warn "remove-listener to nil node"))))

(defn add-doc-listener [event handler] (add-listener js/document event handler))
(defn remove-doc-listener [event handler] (add-listener js/document event handler))

(defn download [url filename]
  (let [a (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.click a)))

(defn download-data [data content-type filename]
  (let [blob (new js/Blob (clj->js [data]) (clj->js {:type content-type}))
        url  (.createObjectURL js/URL blob)]
    (download url filename)
    (timeout 100 #(.revokeObjectURL js/URL url))))

(defn copy-to-clipboard-fallback [text]
  (let [textarea (.createElement js/document "textarea")
        body     (.-body js/document)]
    (set! (.-textContent textarea) text)
    (.appendChild body textarea)
    (let [selection (.getSelection js/document)
          range     (.createRange js/document)]
      (.selectNode range textarea)
      (.removeAllRanges selection)
      (.addRange selection range)
      (.execCommand js/document "copy")
      (.removeAllRanges selection)
      (.removeChild body textarea))))

(defn copy-to-clipboard [text]
  (if-let [clipboard (.-clipboard js/navigator)]
    (.writeText clipboard text)
    (copy-to-clipboard-fallback text)))

;; ----- Painting on Canvas -----

(defn begin-path! [ctx] (.beginPath ctx))
(defn stroke! [ctx] (.stroke ctx))
(defn fill! [ctx] (.fill ctx))
(defn line-width= [ctx w] (set! (.-lineWidth ctx) w))
(defn stroke-color= [ctx color] (set! (.-strokeStyle ctx) color))
(defn fill-color= [ctx color] (set! (.-fillStyle ctx) color))
(defn font= [ctx font] (set! (.-font ctx) font))
(defn text-align= [ctx align] (set! (.-textAlign ctx) align))
(defn close-path! [ctx] (.closePath ctx))
(defn move-to! [ctx [x y]] (.moveTo ctx x y))
(defn line-to! [ctx [x y]] (.lineTo ctx x y))
(defn fill-rect! [ctx [x1 y1] [x2 y2]] (.fillRect ctx x1 y1 x2 y2))
(defn stroke-rect! [ctx [x1 y1] [x2 y2]] (.strokeRect ctx x1 y1 x2 y2))
(defn fill-text! [ctx text [x y]] (.fillText ctx text x y))

;; ^^^^^ Painting on Canvas ^^^^^

(defn scroll-into-view
  ([thing] (scroll-into-view thing {:behavior "smooth"}))
  ([thing options] (.scrollIntoView (resolve-node thing) (clj->js options))))

(defn scroll-to-top [] (.scrollTo js/window (clj->js {:behavior "smooth" :top 0})))

(def console-log (partial (.-log js/console)))
