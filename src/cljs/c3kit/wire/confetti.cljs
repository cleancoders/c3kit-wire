(ns c3kit.wire.confetti
  (:require [c3kit.apron.time :as time]
            [c3kit.wire.js :as wjs]))

(def width #(.-innerWidth js/window))
(def height #(.-innerHeight js/window))

(def confetti-colors [[30 144 255] [107 142 35] [255 215 0] [255 192 203] [106 90 205] [173 216 230] [238 130 238] [152 251 152] [70 130 180] [244 164 96] [210 105 30] [220 20 60]])
(defn- create-element [type] (.createElement js/document type))
(defn- set-attribute! [element type value] (.setAttribute element type value))
(defn- set-width! [canvas] (set! (.-width canvas) (width)))
(defn- set-height! [canvas] (set! (.-height canvas) (height)))
(defn -request-animation-frame [callback] (.requestAnimationFrame js/window callback))
(defn -prepend [element] (.prepend (.-body js/document) element))
(defn -remove-from-dom [element] (.remove element))
(defn- clear-rect [context w h] (.clearRect context 0 0 w h))
(defn performance-now [] (js-invoke js/performance "now"))
(def max-sparkles 200)

(defn -pick-a-color []
  (let [color (conj (rand-nth confetti-colors) (rand 1))]
    (str "rgba(" (apply str (interpose ", " color)) ")")))

(def base-sparkle
  {:window-w   (width)
   :window-h   (height)
   :wave-angle 0})

(defn x-pos [transform] (-> transform :position :x))
(defn y-pos [transform] (-> transform :position :y))
(defn x-vel [transform] (-> transform :velocity :x))
(defn y-vel [transform] (-> transform :velocity :y))
(defn x-accel [transform] (-> transform :acceleration :x))
(defn y-accel [transform] (-> transform :acceleration :y))

(defn- draw-sparkle! [context {:keys [diameter transform tilt colors] :as sparkle}]
  (let [x     (x-pos transform)
        y     (y-pos transform)
        r     (/ diameter 2)
        x2    (+ x tilt)
        x     (+ x2 r)
        y2    (+ y tilt r)
        color (first colors)]
    (wjs/begin-path! context)
    (wjs/stroke! context)
    (wjs/line-width= context diameter)
    (wjs/stroke-color= context color)
    (wjs/move-to! context [x y])
    (wjs/line-to! context [x2 y2])
    (wjs/stroke! context)))

(defn- draw-confetti! [canvas sparkles]
  (let [context (wjs/context-2d canvas)
        w       (width)
        h       (height)]
    (clear-rect context w h)
    (doseq [sparkle sparkles]
      (draw-sparkle! context sparkle))))

(defn- sparkle-has-landed? [{:keys [transform]}]
  (> (y-pos transform) (height)))

(def baseline-fps (/ 60 1000))
(def fall-speed 0.65)

(defn calculate-time-correction [elapsed]
  (* elapsed baseline-fps))

(defn calculate-fall-coefficient [elapsed]
  (* (calculate-time-correction elapsed) fall-speed))

(defn calculate-wave-adjustment [elapsed]
  (/ (calculate-time-correction elapsed) 50))

(defn calculate-new-wave-angle [wave-angle elapsed]
  (+ (calculate-wave-adjustment elapsed) wave-angle))

(defn calculate-new-tilt-angle [tilt-angle tilt-angle-inc elapsed]
  (+ tilt-angle (* tilt-angle-inc (calculate-fall-coefficient elapsed) 2)))

(defn calculate-drop-x [x wave-angle elapsed x-vel longer-time]
  (let [value             (- (Math/sin wave-angle) (calculate-fall-coefficient elapsed))
        sinusoidal        (if (> x-vel 0) value (- value))
        w                 (width)
        x-vel-coefficient (* 0.1 (/ w 1302))]
    (+ x sinusoidal (* x-vel-coefficient longer-time x-vel))))

(defn calculate-drop-y [y wave-angle diameter elapsed]
  (+ y (* (calculate-fall-coefficient elapsed) (+ 2 (Math/cos wave-angle) diameter))))

(defn calculate-flight-x [x x-vel time]
  (+ x (* x-vel time)))

;yf = yi -vi*t + 1/2a*t^2
(defn calculate-flight-y [y initial-y-vel gravity extended-time]
  (+ y (* -1 initial-y-vel extended-time) (* 0.5 gravity (* extended-time extended-time))))

(defn calculate-y-position [{:keys [wave-angle transform diameter] :as _sparkle} dropping? elapsed extended-time y-vel]
  (if dropping?
    (calculate-drop-y (y-pos transform) wave-angle diameter elapsed)
    (calculate-flight-y (y-pos transform) y-vel (y-accel transform) extended-time)))

(defn calculate-x-position [{:keys [wave-angle transform] :as _sparkle} dropping? elapsed extended-time x-vel]
  (if dropping?
    (calculate-drop-x (x-pos transform) wave-angle elapsed x-vel extended-time)
    (calculate-flight-x (x-pos transform) x-vel extended-time)))

(defn calculate-new-y-speed [transform time]
  (- (y-vel transform) (* (y-accel transform) time)))

(defn rand-between [min max]
  (let [difference (- max min)]
    (+ min (* (rand) difference))))

(defn should-drop? [drop y-vel]
  (let [threshold   (rand-between -5 12)
        drop-chance 0.997]
    (or drop (> (rand 1) drop-chance) (< y-vel threshold))))

(defmulti -calculate-new-transform (fn [sparkle & _] (:kind sparkle)))

(defmethod -calculate-new-transform :drop [{:keys [transform wave-angle diameter] :as sparkle} elapsed]
  (let [wave-angle (rand-between (- wave-angle) wave-angle)
        x-pos      (+ (x-pos transform) (Math/sin wave-angle))
        y-pos      (calculate-drop-y (y-pos transform) wave-angle diameter elapsed)]
    (assoc transform :position {:x x-pos :y y-pos})))

(defmethod -calculate-new-transform :cannon [{:keys [transform] :as sparkle} elapsed]
  (let [x-vel         (+ (x-vel transform) (x-accel transform))
        extended-time (/ elapsed 50)
        y-vel         (calculate-new-y-speed transform extended-time)
        dropping?     (should-drop? (:drop? transform) y-vel)
        y-pos         (calculate-y-position sparkle dropping? elapsed extended-time y-vel)
        x-pos         (calculate-x-position sparkle dropping? elapsed extended-time x-vel)]
    (assoc transform :position {:x x-pos :y y-pos}
                     :velocity {:x x-vel :y y-vel}
                     :drop? dropping?)))

(defmethod -calculate-new-transform :bomb [{:keys [transform] :as _sparkle} elapsed]
  (let [x-pos   (x-pos transform)
        y-pos   (y-pos transform)
        x-vel   (x-vel transform)
        y-vel   (y-vel transform)
        x-accel (x-accel transform)
        y-accel (y-accel transform)]
    (assoc transform :position {:x (+ x-pos x-vel) :y (+ y-pos y-vel)}
                     :velocity {:x x-vel :y (+ y-vel y-accel)}
                     :acceleration {:x x-accel :y y-accel})))

(defmulti -update-sparkle :kind)

(defmethod -update-sparkle :drop [{:keys [wave-angle tilt-angle tilt-angle-inc last-time] :as sparkle}]
  (when-not (sparkle-has-landed? sparkle)
    (let [now     (performance-now)
          elapsed (- now last-time)]
      (assoc sparkle :last-time now
                     :wave-angle (calculate-new-wave-angle wave-angle elapsed)
                     :tilt-angle (calculate-new-tilt-angle tilt-angle tilt-angle-inc elapsed)
                     :transform (-calculate-new-transform sparkle elapsed)
                     :tilt (* 15 (Math/sin tilt-angle))))))

(defmethod -update-sparkle :cannon [{:keys [wave-angle tilt-angle tilt-angle-inc last-time] :as sparkle}]
  (when-not (sparkle-has-landed? sparkle)
    (let [now     (performance-now)
          elapsed (- now last-time)]
      (if (zero? elapsed)
        sparkle
        (assoc sparkle :last-time now
                       :wave-angle (calculate-new-wave-angle wave-angle elapsed)
                       :tilt-angle (calculate-new-tilt-angle tilt-angle tilt-angle-inc elapsed)
                       :transform (-calculate-new-transform sparkle elapsed)
                       :tilt (* 15 (Math/sin tilt-angle)))))))

(defmethod -update-sparkle :bomb [{:keys [invisible? ball-radius max-ball-radius start-time
                                          wave-angle tilt-angle tilt-angle-inc last-time] :as sparkle}]
  (when-not (sparkle-has-landed? sparkle)
    (let [now          (performance-now)
          time-ratio   (/ (- now start-time) (time/seconds 1))
          radius-ratio (/ ball-radius max-ball-radius)
          visible?     (or (not invisible?) (>= time-ratio radius-ratio))
          exploded?    (>= time-ratio 1)
          elapsed      (- now last-time)]
      (merge sparkle
             {:last-time  now
              :invisible? (not visible?)}
             (when exploded? {:transform  (-calculate-new-transform sparkle elapsed)
                              :wave-angle (calculate-new-wave-angle wave-angle elapsed)
                              :tilt-angle (calculate-new-tilt-angle tilt-angle tilt-angle-inc elapsed)
                              :tilt       (* 6 (Math/sin tilt-angle))})))))

(defn -update-sparkles [sparkles]
  (keep -update-sparkle sparkles))

(defn -animate! [canvas sparkles]
  (let [new-sparkles (-update-sparkles sparkles)]
    (if (empty? new-sparkles)
      (-remove-from-dom canvas)
      (do
        (draw-confetti! canvas (remove :invisible? new-sparkles))
        (-request-animation-frame #(-animate! canvas new-sparkles))))))

(defn merge-sparkle [& maps]
  (apply merge
         (concat
           [base-sparkle
            {:diameter       (rand-between 5 15)
             :colors         (repeatedly 2 -pick-a-color)
             :tilt           (rand-between -10 0)
             :tilt-angle-inc (rand-between 0.05 0.12)
             :tilt-angle     (* (rand) Math/PI)}]
           maps)))

(defmulti -create-sparkle (fn [& [kind]] kind))

(defmethod -create-sparkle :drop [_ w h]
  (merge-sparkle {:last-time (performance-now)
                  :kind      :drop
                  :transform {:position     {:x (* w (rand 1)) :y (* -1 h (rand 0.2))}
                              :velocity     {:x (rand-between -0.5 0.5) :y 0}
                              :acceleration {:x 0 :y 0}
                              :drop?        true}}))

(defmethod -create-sparkle :cannon [_ h x-pos-init x-vel-init]
  (let [y-normalize   (/ h 1028)
        initial-y-vel (* y-normalize (rand-between 70 90))]
    (merge-sparkle {:last-time (performance-now)
                    :kind      :cannon
                    :transform {:position     {:x x-pos-init :y h}
                                :velocity     {:x x-vel-init :y initial-y-vel}
                                :acceleration {:x 0 :y (* y-normalize (rand-between 3 4))}
                                :drop?        false}})))

(defmethod -create-sparkle :bomb [_ center-x center-y]
  (let [max-radius 50
        radius     (rand-between 0 max-radius)
        angle      (rand-between 0 (* 2 Math/PI))
        delta-x    (* radius (Math/cos angle))
        delta-y    (* radius (Math/sin angle))
        now        (performance-now)]
    (merge-sparkle {:last-time       now
                    :start-time      now
                    :kind            :bomb
                    :transform       {:position     {:x (+ center-x delta-x) :y (+ center-y delta-y)}
                                      :velocity     {:x (/ delta-x 2.5) :y (/ delta-y 2.5)}
                                      :acceleration {:x 0 :y 0.1}}
                    :invisible?      true
                    :max-ball-radius max-radius
                    :ball-radius     radius})))

(defn- set-size [canvas]
  (set-width! canvas)
  (set-height! canvas))

(defn -create-canvas! []
  (let [canvas (create-element "canvas")]
    (set-attribute! canvas "id" "-confetti-canvas")
    (set-attribute! canvas "style" "display:block; z-index:999999;pointer-events:none;position:fixed")
    (set-size canvas)
    (.addEventListener js/window "resize" #(set-size canvas) true)
    canvas))

(defn animate-canvas [canvas sparkles]
  (-prepend canvas)
  (-request-animation-frame #(-animate! canvas sparkles)))

(defmulti simulate! (fn [kind] kind))

(defmethod simulate! :drop [_]
  (let [canvas   (-create-canvas!)
        sparkles (repeatedly (* max-sparkles 1.5) #(-create-sparkle :drop (width) (height)))]
    (animate-canvas canvas sparkles)))

(defmethod simulate! :cannon [_]
  (let [canvas         (-create-canvas!)
        [w h] [(width) (height)]
        x-normalize    (/ w 1302)
        x-offset       (* x-normalize 300)
        left-sparkles  (repeatedly max-sparkles #(-create-sparkle :cannon h (- x-offset) (* x-normalize (rand-between 40 50))))
        right-sparkles (repeatedly max-sparkles #(-create-sparkle :cannon h (+ w x-offset) (* -1 x-normalize (rand-between 40 50))))
        sparkles       (concat left-sparkles right-sparkles)]
    (animate-canvas canvas sparkles)))

(defmethod simulate! :bomb [_]
  (let [canvas   (-create-canvas!)
        [w h] [(width) (height)]
        sparkles (repeatedly (* max-sparkles 3) #(-create-sparkle :bomb (/ w 2) (/ h 2.75)))]
    (animate-canvas canvas sparkles)))