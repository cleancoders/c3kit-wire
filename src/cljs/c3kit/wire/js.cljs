(ns c3kit.wire.js
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.apron.schema :as schema]
            [c3kit.apron.time :as time]
            [cljs-http.client :as http]
            [clojure.string :as str]
            [clojure.string :as s]
            [goog.object :as gobject])
  (:import (goog History)))

;; ----- Key Codes and Events -----

(defn e-key-code
  "DEPRECATED: Use 'e-code' instead."
  [e] (.-keyCode e))
(defn e-code [e] (.-code e))
(defn alt-key?
  "On macOS, this is the ⌥ Option key."
  [e] (.-altKey e))
(defn meta-key?
  "On macOS, this is the ⌘ Command key."
  [e] (.-metaKey e))
(defn ctrl-key? [e] (.-ctrlKey e))
(defn shift-key? [e] (.-shiftKey e))

(def BACKSPACE "Backspace")
(def TAB "Tab")
(def ENTER "Enter")
(def SHIFTLEFT "ShiftLeft")
(def SHIFTRIGHT "ShiftRight")
(def ESC "Escape")
(def SPACE "Space")
(def LEFT "ArrowLeft")
(def UP "ArrowUp")
(def RIGHT "ArrowRight")
(def DOWN "ArrowDown")
(def DELETE "Delete")
(def DIGIT0 "Digit0")
(def DIGIT1 "Digit1")
(def DIGIT2 "Digit2")
(def DIGIT3 "Digit3")
(def DIGIT4 "Digit4")
(def DIGIT5 "Digit5")
(def DIGIT6 "Digit6")
(def DIGIT7 "Digit7")
(def DIGIT8 "Digit8")
(def DIGIT9 "Digit9")
(def NUMPAD+ "NumpadAdd")
(def COMMA "Comma")

(defn e-code?
  ([code] (fn [e] (e-code? code e)))
  ([code e] (= (e-code e) code)))
(defn BACKSPACE? [e] (e-code? BACKSPACE e))
(defn TAB? [e] (e-code? TAB e))
(defn ENTER? [e] (e-code? ENTER e))
(defn SHIFT? [e] (or (e-code? SHIFTLEFT e) (e-code? SHIFTRIGHT e)))
(defn ESC? [e] (e-code? ESC e))
(defn SPACE? [e] (e-code? SPACE e))
(defn LEFT? [e] (e-code? LEFT e))
(defn UP? [e] (e-code? UP e))
(defn RIGHT? [e] (e-code? RIGHT e))
(defn DOWN? [e] (e-code? DOWN e))
(defn DELETE? [e] (e-code? DELETE e))
(defn NUMPAD+? [e] (e-code? NUMPAD+ e))
(defn COMMA? [e] (e-code? COMMA e))

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

(defn o-get-in
  ([js-obj ks] (reduce o-get js-obj ks))
  ([js-obj ks default] (or (o-get-in js-obj ks) default)))

(defn o-set [js-obj key value] (gobject/set js-obj key value))

(defn o-update!
  "Update a JavaScript object key according to a function, f,
   applied to the current value followed by args."
  [obj k f & args]
  (o-set obj k (apply f (o-get obj k) args)))

(defn o-dissoc!
  "Dissoc a key in a JavaScript object according to a path of keys."
  [obj key]
  (js-delete obj key)
  obj)

(defn o-merge!
  "Returns the first object with the subsequent objects merged onto it"
  [obj other & others]
  (cond
    (map? other)
    (doseq [[k v] (seq other)] (o-set obj k v))
    (instance? js/Object other)
    (doseq [k (.keys js/Object other)] (o-set obj k (o-get other k))))
  (if (seq others)
    (apply o-merge! obj others)
    obj))

(defn- get-or-create-node [obj k]
  (or (o-get obj k)
      (let [node (js/Object.)]
        (o-set obj k node)
        node)))

(defn o-assoc-in!
  "Assoc a nested value in a JavaScript object according to a path of keys."
  [obj ks v]
  (let [key-path (butlast ks)
        node     (reduce get-or-create-node obj key-path)
        key      (last ks)]
    (o-set node key v)))

(defn o-dissoc-in!
  "Dissoc a nested value in a JavaScript object according to a path of keys."
  [obj ks]
  (some-> (o-get-in obj (butlast ks))
          (js-delete (last ks)))
  obj)

(defn user-agent
  ([] (user-agent js/navigator))
  ([navigator] (.-userAgent navigator)))

(defn- user-agent-match?
  ([re] (user-agent-match? re (user-agent)))
  ([re user-agent] (boolean (re-find re user-agent))))

(defn mac-os? [] (user-agent-match? #"\((Macintosh|MacIntel|MacPPC|Mac68K)"))
(defn win-os? [] (user-agent-match? #"\((Windows|Win32|Win64|WinCE)"))
(defn linux? [] (user-agent-match? #"\(Linux"))
(defn ios? [] (user-agent-match? #"\((iPhone|iPad|iPod)"))
(defn android? [] (user-agent-match? #"\(Android"))

;; ----- Simple js function translations -----

(defn clear-interval [interval] (js/clearInterval interval))
(defn clear-timeout [timeout] (js/clearTimeout timeout))
(defn context-2d [canvas] (.getContext canvas "2d"))
(defn active-element [] (.-activeElement js/document))
(defn doc-body
  ([] (.-body js/document))
  ([doc] (.-body doc)))
(defn doc-cookie [] (.-cookie js/document))
(defn doc-ready-state [] (.-readyState js/document))
(defn doc-ready? [] (= "complete" (doc-ready-state)))
(defn document ([] js/document) ([node] (.-ownerDocument node)))
(defn e-checked? [e] (-> e .-target .-checked))
(defn e-delta-y [e] (.-deltaY e))
(defn e-client-x [e] (.-clientX e))
(defn e-client-y [e] (.-clientY e))
(defn e-coordinates [e] [(.-clientX e) (.-clientY e)])
(defn e-related-target [e] (.-relatedTarget e))
(defn e-left-click? [e] (= 0 (o-get e "button")))
(defn e-wheel-click? [e] (= 1 (o-get e "button")))
(defn e-right-click? [e] (= 2 (o-get e "button")))
(defn e-target [e] (.-target e))
(defn e-text [e] (-> e .-target .-value))
(defn e-type [e] (.-type e))
(defn- file->clj [file]
  (ccc/remove-nils
    {:name                 (.-name file)
     :type                 (.-type file)
     :size                 (.-size file)
     :last-modified        (.-lastModified file)
     :last-modified-date   (.-lastModifiedDate file)
     :webkit-relative-path (.-webkitRelativePath file)
     :file                 file}))
(defn e-files [e]
  (->> (or (some->> e .-dataTransfer .-files)
           (some->> e .-target .-files)
           [])
       (map file->clj)))
(defn element-by-id [id] (.getElementById js/document id))
(defn frame-window [iframe] (.-contentWindow iframe))
(defn interval [millis f] (js/setInterval f millis))
(defn node-add-class [node class] (.add (.-classList node) class))
(defn node-append-child [node child] (.appendChild node child))
(defn node-children [node] (array-seq (.-childNodes node)))
(defn node-classes [node] (.-className node))
(defn node-clone [node deep?] (.cloneNode node deep?))
(defn node-files [node] (.-files node))
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
(defn page-hash [] (-> js/window .-location .-hash))
(defn page-pathname [] (-> js/window .-location .-pathname))
(defn page-reload! [] (.reload (.-location js/window)))
(defn page-title [] (.-title js/document))
(defn page-title= [title] (set! (.-title js/document) title))
(defn post-message [window message target-domain] (.postMessage window (clj->js message) target-domain))
(defn print-page [] (.print js/window))
(defn query-selector [selector] (.querySelector js/document selector))
(defn query-selector-all [selector] (.querySelectorAll js/document selector))
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

(defn focus!
  "Resolved the node and focus.
  Options: {:preventScroll true ;; default is false
            :focusVisible true}  ;; default may be false "
  ([thing] (some-> thing resolve-node (.focus)))
  ([thing options] (some-> thing resolve-node (.focus (clj->js options)))))
(defn blur! [thing] (some-> thing resolve-node (.blur)))
(defn click! [thing] (some-> thing resolve-node .click))

(defn active?
  "True if the element is the document's activeElement."
  [thing] (= (active-element) (resolve-node thing)))

(defn inactive?
  "True if the element is not the document's activeElement."
  [thing] (not (active? thing)))

(defn add-listener [node event listener & {:as options}]
  (if node
    (.addEventListener node event listener (some-> options clj->js))
    (log/warn "add-listener to nil node")))

(defn remove-listener [node event listener & {:as options}]
  (if node
    (.removeEventListener node event listener (some-> options clj->js))
    (log/warn "remove-listener to nil node")))

(defn add-doc-listener [event handler] (add-listener js/document event handler))
(defn remove-doc-listener [event handler] (remove-listener js/document event handler))

(defn- ->query-value [v] (if (instance? js/Date v) (pr-str v) v))
(defn ->query-string [params] (http/generate-query-string (update-vals params ->query-value)))
(defn encode-url [root params] (cond-> root (seq params) (str "?" (->query-string params))))

(defn download
  ([url filename] (download url filename {}))
  ([url filename params]
   (let [a   (.createElement js/document "a")
         url (encode-url url params)]
     (set! (.-href a) url)
     (set! (.-download a) filename)
     (.click a))))

(defn download-data [data content-type filename]
  (let [blob (new js/Blob (clj->js [data]) (clj->js {:type content-type}))
        url  (.createObjectURL js/URL blob)]
    (download url filename)
    (timeout 100 #(.revokeObjectURL js/URL url))))

(defn ->audio [src]
  (let [audio (js-invoke js/document "createElement" "audio")]
    (o-set audio "src" src)
    audio))

(defn play-audio [audio] (js-invoke audio "play"))
(defn pause-audio [audio] (js-invoke audio "pause"))

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
  ([thing options]
   (some-> (resolve-node thing)
           (.scrollIntoView (clj->js options)))))

(defn scroll-to [thing options]
  (some-> (resolve-node thing)
          (.scrollTo (clj->js options))))

(defn scroll-to-top [] (.scrollTo js/window (clj->js {:behavior "smooth" :top 0})))

(def console-log (partial (.-log js/console)))
(defn ->inspect
  "Insert in threading macro to console log the value."
  [v]
  (console-log "->inspect:" v)
  v)

(defn ->event
  "Creates a JavaScript Event of a given type with optional added attributes"
  [type & {:as kvs}]
  (o-merge! (js/Event. type) kvs))

(defn dispatch-event
  "Dispatches the event to the node.
   If given an event, kvs is merged onto it.
   If given a string, creates a new event of that type with kvs."
  [node e & {:as kvs}]
  (let [e (if (string? e)
            (->event e kvs)
            (o-merge! e kvs))]
    (js-invoke node "dispatchEvent" e)))

(defn- ->replacer [replacer]
  (cond
    (fn? replacer) replacer
    (map? replacer) (fn [k v] (get replacer k v))
    :else (clj->js replacer)))

(defn stringify-json
  "Converts a value to a JSON string.
    - value:    The value to convert to JSON.
    - replacer: A 2-arity function of key and value
    - space:    A string or number that is used to insert white space."
  ([value] (stringify-json value nil))
  ([value replacer] (stringify-json value replacer 2))
  ([value replacer space] (.stringify js/JSON (clj->js value) (->replacer replacer) space)))

(defn parse-json
  "Constructs the Clojure value described by the string.
    - value:   The JSON string
    - reviver: A 2-arity function of key and value"
  ([text] (parse-json text nil))
  ([text reviver] (js->clj (.parse js/JSON text (->replacer reviver)))))
