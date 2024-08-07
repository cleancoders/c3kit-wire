(ns c3kit.wire.confetti-spec
  (:require-macros [speclj.core :refer [focus-context before context describe it redefs-around should should-be-nil should-contain should-have-invoked
                                        should-not should-not-have-invoked should-not= should= stub with-stubs focus-it]])
  (:require [c3kit.wire.js :as wjs]
            [clojure.string :as str]
            [c3kit.wire.confetti :as sut]
            [c3kit.wire.spec-helper :as wire]
            [c3kit.wire.spec-helper]
            [speclj.stub :as stub]))

(def rand-ratom (atom 0))

(defmethod sut/-rand :mock [& _]
  @rand-ratom)

(def now-counter (atom 0))
(def rand-nth-counter (atom 0))
(def baseline-fps (/ 60 1000))
(def fall-speed 0.5)

(describe "Confetti"
  (with-stubs)
  (wire/with-root-dom)
  (before (reset! sut/rand-impl :mock)
          (reset! rand-ratom 0)
          (reset! now-counter 0))

  (redefs-around [sut/performance-now #(swap! now-counter inc)
                  rand-nth (fn [coll]
                             (nth coll (mod (swap! rand-nth-counter inc) (count coll))))])

  (it "creates a canvas"
    (let [canvas (sut/-create-canvas!)]
      (should= "-confetti-canvas" (.getAttribute canvas "id"))
      (should= (str (.-innerWidth js/window)) (.getAttribute canvas "width"))
      (should= (str (.-innerHeight js/window)) (.getAttribute canvas "height"))))

  (it "creates a cannon sparkle"
    (let [sparkle (sut/-create-sparkle :cannon 400 1 2)]
      (should= 1 (:last-time sparkle))
      (should= :cannon (:kind sparkle))
      (should= 1 (sut/x-pos (:transform sparkle)))
      (should= 400 (sut/y-pos (:transform sparkle)))
      (should= 2 (sut/x-vel (:transform sparkle)))
      (should= (* (/ 400 1028) 70) (sut/y-vel (:transform sparkle)))
      (should= 5 (:diameter sparkle))
      (should= -10 (:tilt sparkle))
      (should= 0.05 (:tilt-angle-inc sparkle))
      (should= 0 (:tilt-angle sparkle))
      (should-not (= (first (:colors sparkle)) (last (:colors sparkle))))
      (should= 0 (:wave-angle sparkle))))

  (it "creates a bomb sparkle"
    (reset! rand-ratom 1)
    (let [sparkle (sut/-create-sparkle :bomb 100 100)]
      (should= 1 (:last-time sparkle))
      (should= 1 (:start-time sparkle))
      (should= :bomb (:kind sparkle))
      (should= 150 (sut/x-pos (:transform sparkle)))
      (should= 100 (sut/y-pos (:transform sparkle)) 0.0001)
      (should= 20 (sut/x-vel (:transform sparkle)))
      (should= 0 (sut/y-vel (:transform sparkle)) 0.0001)
      (should= 0 (sut/x-accel (:transform sparkle)) 0.0001)
      (should= 0.1 (sut/y-accel (:transform sparkle)) 0.0001)
      (should= 15 (:diameter sparkle))
      (should= 0 (:tilt sparkle))
      (should= 0.12 (:tilt-angle-inc sparkle))
      (should= Math/PI (:tilt-angle sparkle))
      (should-not (= (first (:colors sparkle)) (last (:colors sparkle))))
      (should= 0 (:wave-angle sparkle))
      (should= 50 (:max-ball-radius sparkle))
      (should= 50 (:ball-radius sparkle))
      (should (:invisible? sparkle))))

  (it "creates a fountain sparkle"
    (reset! rand-ratom 1)
    (let [sparkle (sut/-create-sparkle :fountain 50 100)]
      (should= 1 (:last-time sparkle))
      (should= 1 (:start-time sparkle))
      (should= :fountain (:kind sparkle))
      (should= 50 (sut/x-pos (:transform sparkle)))
      (should= 100 (sut/y-pos (:transform sparkle)) 0.0001)
      (should= 0 (sut/x-vel (:transform sparkle)))
      (should= (* (/ 100 1028) 80) (sut/y-vel (:transform sparkle)) 0.0001)
      (should= 0 (sut/x-accel (:transform sparkle)) 0.0001)
      (should= (* (/ 100 1028) 4) (sut/y-accel (:transform sparkle)) 0.0001)
      (should= 15 (:diameter sparkle))
      (should= 0 (:tilt sparkle))
      (should= 0.12 (:tilt-angle-inc sparkle))
      (should= Math/PI (:tilt-angle sparkle))
      (should-not (= (first (:colors sparkle)) (last (:colors sparkle))))
      (should= 0 (:wave-angle sparkle))))

  (it "creates a fireworks sparkle"
    (reset! rand-ratom 1)
    (let [sparkle (sut/-create-sparkle :fireworks 100 200)]
      (should= 1 (:last-time sparkle))
      (should= 1 (:start-time sparkle))
      (should= :fireworks (:kind sparkle))
      (should (sut/x-pos (:transform sparkle)))
      (should (sut/y-pos (:transform sparkle)))
      (should (sut/x-vel (:transform sparkle)))
      (should (sut/y-vel (:transform sparkle)))
      (should= 0 (sut/x-accel (:transform sparkle)) 0.0001)
      (should= 0.1 (sut/y-accel (:transform sparkle)) 0.0001)
      (should= 0 (:tilt sparkle))
      (should= 0.12 (:tilt-angle-inc sparkle))
      (should= Math/PI (:tilt-angle sparkle))
      (should-not (= (first (:colors sparkle)) (last (:colors sparkle))))
      (should= 0 (:wave-angle sparkle))))

  (context "drop sparkle"
    (before (reset! rand-ratom 1))
    (redefs-around [sut/width (constantly 400)
                    sut/height (constantly 300)
                    sut/performance-now (fn [] 2)])

    (it "creates a drop sparkle with correct initial properties"
      (let [[w h] [(sut/width) (sut/height)]
            sparkle (sut/-create-sparkle :drop w h)]
        (should= :drop (:kind sparkle))
        (should= 2 (:last-time sparkle))
        (should= {:x w :y (- h)}
                 (:position (:transform sparkle)))
        (should= {:x 0.5 :y 0}
                 (:velocity (:transform sparkle)))
        (should= {:x 0 :y 0}
                 (:acceleration (:transform sparkle)))
        (should= true (:drop? (:transform sparkle)))
        (should= 0 (:wave-angle sparkle))
        (should= 15 (:diameter sparkle))
        (should= 0 (:tilt sparkle))
        (should= 0.12 (:tilt-angle-inc sparkle))
        (should= Math/PI (:tilt-angle sparkle))))

    (it "updates a drop sparkle correctly"
      (let [sparkle         (sut/-create-sparkle :drop (sut/width) (sut/height))
            updated-sparkle (sut/-update-sparkle sparkle)
            elapsed         (- (:last-time updated-sparkle) (:last-time sparkle))]
        (should= 2 (:last-time updated-sparkle))
        (should= (+ (/ (* baseline-fps elapsed) 50) (:wave-angle sparkle))
                 (:wave-angle updated-sparkle))
        (should= (+ (:tilt-angle sparkle) (* (:tilt-angle-inc sparkle) elapsed baseline-fps fall-speed 2))
                 (:tilt-angle updated-sparkle))
        (should= (* 15 (Math/sin (:tilt-angle updated-sparkle)))
                 (:tilt updated-sparkle))
        (should= (- (sut/x-pos (:transform sparkle))
                    (- (Math/sin (:wave-angle sparkle)) (* elapsed baseline-fps 0.5)))
                 (sut/x-pos (:transform updated-sparkle)))
        (should= (+ (sut/y-pos (:transform sparkle))
                    (* elapsed baseline-fps 0.5 (+ (Math/cos (:wave-angle sparkle)) (:diameter sparkle))))
                 (sut/y-pos (:transform updated-sparkle))))))

  (context "updates sparkle"
    (redefs-around [sut/width (constantly 400)
                    sut/height (constantly 300)])

    (it "flying"
      (let [sparkle         (sut/-create-sparkle :cannon (sut/height) 0 100)
            updated-sparkle (sut/-update-sparkle sparkle)
            elapsed         (- (:last-time updated-sparkle) (:last-time sparkle))
            extended-time   (/ elapsed 50)
            transform       (:transform sparkle)
            y-vel           (- (sut/y-vel transform) (* (sut/y-accel transform) extended-time))]
        (should= 2 (:last-time updated-sparkle))
        (should= (* 15 (Math/sin (:tilt-angle sparkle)))
                 (:tilt updated-sparkle))
        (should= (+ (/ (* baseline-fps elapsed) 50) (:wave-angle sparkle))
                 (:wave-angle updated-sparkle))
        (should= (+ (:tilt-angle sparkle) (* (:tilt-angle-inc sparkle) elapsed baseline-fps fall-speed 2))
                 (:tilt-angle updated-sparkle))
        (should= (+ (sut/x-pos (:transform sparkle)) (* (sut/x-vel (:transform sparkle)) extended-time))
                 (sut/x-pos (:transform updated-sparkle)))
        (should= (+ (sut/y-pos (:transform sparkle)) (* -1 y-vel extended-time) (* 0.5 (sut/y-accel transform) (* extended-time extended-time)))
                 (sut/y-pos (:transform updated-sparkle)))
        (should= 100 (sut/x-vel (:transform updated-sparkle)))
        (should= (- (* (/ 300 1028) 70) (* (/ 300 1028) 3 extended-time))
                 (sut/y-vel (:transform updated-sparkle)) 0.0001)))

    (context "dropping"
      (it "with positive x velocity"
        (let [sparkle         (assoc-in (sut/-create-sparkle :cannon (sut/height) (sut/width) 100) [:transform :drop?] true)
              updated-sparkle (sut/-update-sparkle sparkle)
              elapsed         (- (:last-time updated-sparkle) (:last-time sparkle))
              extended-time   (/ elapsed 50)
              transform       (:transform sparkle)]
          (should= 2 (:last-time updated-sparkle))
          (should= (* 15 (Math/sin (:tilt-angle sparkle)))
                   (:tilt updated-sparkle))
          (should= (+ (/ (* baseline-fps elapsed) 50) (:wave-angle sparkle))
                   (:wave-angle updated-sparkle))
          (should= (+ (:tilt-angle sparkle) (* (:tilt-angle-inc sparkle) elapsed baseline-fps fall-speed 2))
                   (:tilt-angle updated-sparkle))
          (should= (+ (sut/x-pos (:transform sparkle))
                      (- (Math/sin (:wave-angle sparkle)) (* 1 baseline-fps fall-speed))
                      (* (* 0.1 (/ (sut/width) 1302)) extended-time (sut/x-vel transform)))
                   (sut/x-pos (:transform updated-sparkle)))
          (should= (+ (sut/y-pos (:transform sparkle))
                      (* elapsed baseline-fps fall-speed (+ 2 (Math/cos (:wave-angle sparkle)) (:diameter sparkle))))
                   (sut/y-pos (:transform updated-sparkle)))))

      (it "with negative x velocity"
        (let [sparkle         (assoc-in (sut/-create-sparkle :cannon (sut/height) (sut/width) -100) [:transform :drop?] true)
              updated-sparkle (sut/-update-sparkle sparkle)
              elapsed         (- (:last-time updated-sparkle) (:last-time sparkle))
              extended-time   (/ elapsed 50)
              transform       (:transform sparkle)]
          (should= (+ (sut/x-pos (:transform sparkle))
                      (- (* 1 baseline-fps fall-speed) (Math/sin (:wave-angle sparkle)))
                      (* (* 0.1 (/ (sut/width) 1302)) extended-time (sut/x-vel transform)))
                   (sut/x-pos (:transform updated-sparkle))))))

    (context "bombing"
      (context "pre-explosion"
        (it "is invisible"
          (reset! rand-ratom 1)
          (let [sparkle         (sut/-create-sparkle :bomb 200 200)
                updated-sparkle (sut/-update-sparkle sparkle)]
            (should= 2 (:last-time updated-sparkle))
            (should (:invisible? updated-sparkle))
            (should-not (:exploded? updated-sparkle))))

        (it "is visible"
          (reset! rand-ratom 0)
          (let [sparkle         (sut/-create-sparkle :bomb 200 200)
                updated-sparkle (sut/-update-sparkle sparkle)]
            (should= 2 (:last-time updated-sparkle))
            (should-not (:invisible? updated-sparkle))
            (should-not (:exploded? updated-sparkle)))))

      (context "post-explosion"
        (it "updates transform"
          (reset! rand-ratom 1)
          (let [sparkle         (sut/-create-sparkle :bomb 200 200)
                _               (dotimes [_ 999] (sut/performance-now))
                updated-sparkle (sut/-update-sparkle sparkle)
                rounding-error  0.0001]
            (should= 1001 (:last-time updated-sparkle))
            (should-not (:invisible? updated-sparkle))
            (should= 270 (sut/x-pos (:transform updated-sparkle)))
            (should= 200 (sut/y-pos (:transform updated-sparkle)) rounding-error)
            (should= 20 (sut/x-vel (:transform updated-sparkle)))
            (should= 0.1 (sut/y-vel (:transform updated-sparkle)) rounding-error)
            (should= 0 (sut/x-accel (:transform updated-sparkle)) rounding-error)
            (should= 0.1 (sut/y-accel (:transform updated-sparkle)) rounding-error)
            (should= 15 (:diameter sparkle))
            (should= 0 (:tilt sparkle))
            (should= 0.12 (:tilt-angle-inc sparkle))
            (should= Math/PI (:tilt-angle sparkle) rounding-error)
            (should-not (= (first (:colors sparkle)) (last (:colors sparkle))))
            (should= 0 (:wave-angle sparkle))))

        (it "removes when landed"
          (reset! rand-ratom 1)
          (let [sparkle         (sut/-create-sparkle :bomb 200 301)
                updated-sparkle (sut/-update-sparkle sparkle)]
            (should-be-nil updated-sparkle)))))

    (context "fireworks explosion"
      (it "updates transform"
        (reset! rand-ratom 1)
        (let [sparkle         (sut/-create-sparkle :fireworks 200 200)
              _               (dotimes [_ 999] (sut/performance-now))
              updated-sparkle (sut/-update-sparkle sparkle)
              rounding-error  0.0001]
          (should= 1001 (:last-time updated-sparkle))
          (should= 270 (sut/x-pos (:transform updated-sparkle)))
          (should= 200 (sut/y-pos (:transform updated-sparkle)) rounding-error)
          (should= 20 (sut/x-vel (:transform updated-sparkle)))
          (should= 0.1 (sut/y-vel (:transform updated-sparkle)) rounding-error)
          (should= 0 (sut/x-accel (:transform updated-sparkle)) rounding-error)
          (should= 0.1 (sut/y-accel (:transform updated-sparkle)) rounding-error)
          (should= 15 (:diameter sparkle))
          (should= 0 (:tilt sparkle))
          (should= 0.12 (:tilt-angle-inc sparkle))
          (should= Math/PI (:tilt-angle sparkle) rounding-error)
          (should-not (= (first (:colors sparkle)) (last (:colors sparkle))))
          (should= 0 (:wave-angle sparkle)))))

    (it "removes when landed"
      (reset! rand-ratom 1)
      (let [sparkle         (sut/-create-sparkle :fireworks 200 301)
            updated-sparkle (sut/-update-sparkle sparkle)]
        (should-be-nil updated-sparkle)))


    (it "fountaining"
      (let [sparkle         (sut/-create-sparkle :fountain 50 100)
            updated-sparkle (sut/-update-sparkle sparkle)
            elapsed         (- (:last-time updated-sparkle) (:last-time sparkle))
            extended-time   (/ elapsed 50)
            transform       (:transform sparkle)]
        (should= 2 (:last-time updated-sparkle))
        (should= (* 15 (Math/sin (:tilt-angle sparkle)))
                 (:tilt updated-sparkle))
        (should= (+ (/ (* baseline-fps elapsed) 50) (:wave-angle sparkle))
                 (:wave-angle updated-sparkle))
        (should= (+ (:tilt-angle sparkle) (* (:tilt-angle-inc sparkle) elapsed baseline-fps fall-speed 2))
                 (:tilt-angle updated-sparkle))
        (let [sign           (if (pos? (- (/ (:window-w sparkle) 2) (:x-pos-init sparkle))) 1 -1)
              curve-modifier (* 0.2 (- 1 (/ (/ (:window-w sparkle) 2) (:x-pos-init sparkle))))
              starting-pos   (* -50 (- 1 (/ (/ (:window-w sparkle) 2) (:x-pos-init sparkle))))]
          (should= (+ (:x-pos-init sparkle) (* 0.3 sign (sut/square (- (* 1 curve-modifier) starting-pos))))
                   (sut/x-pos (:transform updated-sparkle))))
        (should= (+ (sut/y-pos (:transform sparkle)) (* -1 (sut/y-vel transform) extended-time) (* 0.5 (sut/y-accel transform) (* extended-time extended-time)))
                 (sut/y-pos (:transform updated-sparkle)) 0.001)
        (should= 0 (sut/x-vel (:transform updated-sparkle)))
        (should= (- (sut/y-vel transform) (* (sut/y-accel transform) (/ 1 50)))
                 (sut/y-vel (:transform updated-sparkle)) 0.0001))
      )
    )

  (it "destroys a sparkles when it hits the floor"
    (should-be-nil (sut/-update-sparkle
                    (assoc (sut/-create-sparkle :cannon 0 0 0)
                      :transform {:position {:y 9999}}))))

  (it "eliminates dead sparkles on floor"
    (let [sparkles [{:kind :cannon :window-h 300 :transform {:position {:y 1000}}}
                    {:kind :cannon :window-h 300 :transform {:position {:y 1000}}}]]
      (should= [] (sut/-update-sparkles sparkles))))

  (it "picks an rgba color"
    (let [color (sut/-pick-a-color)
          split (str/split color #"[() \\s ,]")
          rgb   [(second split) (nth split 3) (nth split 5)]]
      (should= "rgba" (first split))
      (should-not= ["0" "0" "0"] rgb)
      (should (int? (int (second split))))
      (should (int? (int (nth split 3))))
      (should (int? (int (nth split 5))))
      (should (and (>= (last split) 0) (<= (last split) 1)))))

  (context "calls fountain"
    (it "100 times"
      (with-redefs [wjs/timeout (stub :timeout)]
        (sut/simulate-fountain!)
        (should-have-invoked :timeout {:times 100})))

    (it "with sparkles"
      (reset! rand-ratom 1)
      (with-redefs [wjs/timeout (stub :timeout)
                    sut/animate-canvas! (stub :animate-canvas)
                    sut/width (stub :width {:return 100})
                    sut/height (stub :height {:return 200})
                    sut/-create-canvas! (stub :create-canvas {:return :canvas})
                    sut/performance-now (stub :performance-now {:return 1})
                    sut/-pick-a-color (stub :pick-a-color {:return :red})]
        (sut/simulate-fountain!)
        ((last (stub/last-invocation-of :timeout)))
        (let [sparkles (repeatedly (/ 200 20) #(sut/-create-sparkle :fountain (* (/ 100 2) 1.10) 200))]
          (should= (concat sparkles sparkles)
                   (deref (last (stub/last-invocation-of :animate-canvas))))
          (should-have-invoked :width)
          (should-have-invoked :height)
          (should-have-invoked :performance-now)
          (should-have-invoked :create-canvas)
          (should-have-invoked :pick-a-color)))))

  (context "calls fireworks"
    (it "8 times"
      (let [sparkles    (atom (sut/create-fireworks-sparkles (/ 10 2) [2 1]))]
       (with-redefs [wjs/timeout (stub :timeout)]
        (sut/simulate-fireworks!)
        (sut/schedule-fireworks sparkles 10 2)
        (should-have-invoked :timeout {:times 8}))))

    (it "with sparkles"
      (reset! rand-ratom 1)
      (with-redefs [wjs/timeout (stub :timeout)
                    sut/animate-canvas! (stub :animate-canvas)
                    sut/width (stub :width {:return 100})
                    sut/height (stub :height {:return 200})
                    sut/-create-canvas! (stub :create-canvas {:return :canvas})
                    sut/performance-now (stub :performance-now {:return 1})
                    sut/-pick-a-color (stub :pick-a-color {:return :red})]
        (sut/simulate-fireworks!)
        ((last (stub/last-invocation-of :timeout)))
        (let [sparkles (repeatedly (/ 200 2) #(sut/-create-sparkle :fireworks (- 100 (/ 100 6)) (- 200 (/ 200 6))))]
          (should= sparkles
                   (deref (last (stub/last-invocation-of :animate-canvas))))
          (should-have-invoked :width)
          (should-have-invoked :height)
          (should-have-invoked :performance-now)
          (should-have-invoked :create-canvas)
          (should-have-invoked :pick-a-color)))))

  (context "stopping animation"

    (redefs-around [sut/-request-animation-frame (stub :request-animation-frame)
                    sut/-remove-from-dom (stub :remove-from-dom)])

    (it "animates when active"
      (sut/-animate (sut/-create-canvas!) (repeatedly 5 #(sut/-create-sparkle :cannon 300 0 0)))
      (should-have-invoked :request-animation-frame)
      (should-not-have-invoked :remove-from-dom))

    (it "doesn't animate when not active"
      (sut/-animate :canvas [])
      (should-not-have-invoked :request-animation-frame)
      (should-have-invoked :remove-from-dom)))

  (context "stateful animate"

    (redefs-around [sut/-request-animation-frame (stub :request-animation-frame)
                    sut/-remove-from-dom (stub :remove-from-dom)
                    sut/width (stub :width {:return 400})
                    sut/height (stub :height {:return 400})
                    sut/performance-now (stub :performance-now {:return 1})
                    sut/-pick-a-color (stub :pick-a-color {:return :red})])

    (it "animates when active"
      (let [sparkles (atom (repeatedly 5 #(sut/-create-sparkle :cannon 300 0 0)))]
        (sut/-animate! (sut/-create-canvas!) sparkles)
        (should= (sut/-update-sparkles @sparkles) @sparkles)
        (should-have-invoked :request-animation-frame)
        (should-not-have-invoked :remove-from-dom)))

    (it "doesn't animate when not active"
      (let [sparkles (atom [])]
        (sut/-animate! :canvas sparkles)
        (should-not-have-invoked :request-animation-frame)
        (should-have-invoked :remove-from-dom)))))
