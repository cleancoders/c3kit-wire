(ns hooks.redis
  (:require [clj-kondo.hooks-api :as api]))

(defn borrow-from
  "c3kit.wire.redis/borrow-from is `(borrow-from [sym pool-expr] & body)` — a
   single-binding-pair macro shaped exactly like `let`. clj-kondo can't see
   that `bindings` introduces a local without understanding the macro, so
   rewrite the call to a `let` with the same binding vector — that resolves
   the bound symbol (and any type hint on it) in `body` with no behaviour
   change."
  [{:keys [node]}]
  (let [[_ bindings & body] (:children node)
        new-node (api/list-node
                  (list* (api/token-node 'let)
                         bindings
                         body))]
    {:node (with-meta new-node (meta node))}))

(defn wait-for-all
  "c3kit.wire.redis/wait-for-all is `(wait-for-all [binding-form coll] form)` —
   a single-iteration binding macro shaped exactly like `for`. clj-kondo can't
   resolve the (possibly destructured) binding without understanding the
   macro, so rewrite the call to a `for` with the same binding vector — that
   resolves the bound symbols in `form` with no behaviour change."
  [{:keys [node]}]
  (let [[_ bindings form] (:children node)
        new-node (api/list-node
                  (list (api/token-node 'for) bindings form))]
    {:node (with-meta new-node (meta node))}))
