(ns c3kit.wire.core.ajax)

(defn make-state
  ([] (make-state cljs.core/atom))
  ([atom-fn] {:active-requests (atom-fn 0)}))
