(ns c3kit.wire.confetti-demo
  (:require
    [c3kit.apron.corec :as ccc]
    [c3kit.apron.log :as log]
    [c3kit.wire.confetti :as confetti]
    [c3kit.wire.js :as wjs]
    [reagent.dom :as dom]))

(defn get-element-by-id [id items] (ccc/ffilter #(or (= id (keyword (:id %))) (= id (:id %))) items))
(defn get-first-element-id [state owner] (get-in @state [owner :first-item]))
(defn get-first-element [state items owner] (-> (get-first-element-id state owner) (get-element-by-id items)))
(defn get-elements-by-owner [owner items] (ccc/find-by items :owner owner))


(defn content []
  [:div
   [:div
    [:h1 "Confetti Demo"]]
   [:br]
   [:button {:on-click #(confetti/simulate-drop!)} "Drop Confetti"]
   [:button {:on-click #(confetti/simulate-cannon!)} "Cannon Confetti"]
   [:button {:on-click #(confetti/simulate-bomb!)} "Bomb Confetti"]
   [:button {:on-click #(confetti/simulate-fountain!)} "Fountain Confetti"]
   [:button {:on-click #(confetti/simulate-fireworks!)} "Fireworks Confetti"]])

(defn ^:export init []
  (dom/render [content] (wjs/element-by-id "main"))
  (log/info "Demo initialized"))


