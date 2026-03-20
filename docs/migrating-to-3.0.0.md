# Migrating to Wire 3.0 (React 18 / Reagent 2)

This guide covers upgrading consumer projects from wire 2.x (React 16 + Reagent 1) to wire 3.0 (React 18 + Reagent 2).

## Dependency Updates

Update your `deps.edn`:

```clojure
;; These come transitively through wire, but if you pin them:
cljsjs/react       {:mvn/version "18.3.1-1"}
cljsjs/react-dom   {:mvn/version "18.3.1-1"}
reagent/reagent     {:mvn/version "2.0.1"}
```

Remove any explicit dependency on `cljsjs/react-dom-test-utils` — it is no longer used.

## What Wire Handles Automatically

Wire 3.0 handles these React 18 concerns internally — **no changes needed in your code**:

- **Functional compiler** — `with-let finally` blocks fire properly on unmount
- **Synchronous cleanup** — event listeners and reactions are cleaned up between tests
- **Listener tracking** — leaked `document`/`window` listeners are removed between tests
- **`act()` scoping** — managed internally, no need to set `IS_REACT_ACT_ENVIRONMENT`
- **Synchronous rendering** — `flushSync` ensures renders commit immediately

## Breaking Changes in spec_helper

### 1. `simulator` var removed

**Before:**
```clojure
((.-mouseDown wire/simulator) (wire/select "#el") (clj->js {:pageX 500}))
((.-keyDown wire/simulator) (wire/select "#input") (clj->js {:shiftKey true :code "Enter" :key "Enter"}))
```

**After:**
```clojure
(wire/mouse-down! "#el" {:pageX 500})
(wire/key-down! js/document "#input" wjs/ENTER {:shiftKey true})
```

All event functions now accept an optional opts map as the last parameter.

### 2. `simulate` / `simulate!` removed

Replace with the specific event function:

```clojure
;; Before
(wire/simulate! "click" "#button" {})

;; After
(wire/click! "#button")
```

### 3. `stub-reset-swap` removed

No longer needed. `wire/reset!` and `wire/swap!` handle flushing directly without arity-specific overrides.

### 4. `mouse-down` signature normalized

The old 3-arity `[root button selector]` is removed. Use opts map instead:

```clojure
;; Before
(wire/mouse-down root 1 "#button")

;; After
(wire/mouse-down! root "#button" {:button 1})
```

### 5. `check-box` uses click events

`check-box` now dispatches a native `click` event instead of `Simulate.change`. It only dispatches when the desired value differs from the current DOM state. This matches real browser behavior where clicking a checkbox toggles its state.

**Impact:** Tests that call `(wire/check-box! selector true)` on an already-checked checkbox will be a no-op. If your test relies on the onChange handler firing regardless of current state, you may need to adjust.

### 6. `change` is now element-type-aware

`change` detects the element type and dispatches the appropriate event:

- **Text/textarea inputs**: sets value via native prototype setter, dispatches `input` event
- **`<select>` elements**: sets value via native setter, dispatches `change` event
- **Checkbox/radio inputs**: dispatches `click` event (only when value differs)
- **File inputs**: unchanged (sets `files` via `Object.defineProperty`, dispatches `change`)

**Impact:** If you have non-React event listeners on `change` events for text inputs, they won't fire — use the DOM `input` event instead. For `<select>` elements, the `change` event is dispatched as before.

### 7. `wire/unmount` replaces `reagent.dom/unmount-component-at-node`

React 18 uses `createRoot` / `root.unmount()` instead of `ReactDOM.unmountComponentAtNode`. The old API is a no-op in React 18. Wire now provides:

```clojure
(wire/unmount)            ;; unmounts the #root container
(wire/unmount container)  ;; unmounts a specific container
```

**Impact:** Any test that calls `reagent.dom/unmount-component-at-node` must switch to `wire/unmount`.

## React 18 Behavioral Changes

React 18 batches ALL state updates by default (not just inside event handlers). In practice this means state changes don't immediately cause re-renders the way they did in React 16.

### Use `wire/reset!` and `wire/swap!` for Reagent Atoms

Use wire's versions instead of `clojure.core/reset!` and `clojure.core/swap!` when modifying atoms that affect rendered components — they flush synchronously after the update:

```clojure
;; Before (may not flush in React 18)
(reset! my-atom new-value)

;; After (flushes synchronously)
(wire/reset! my-atom new-value)
```

### Add `wire/flush` After State Changes in Setup

If your `before` block sets state that components depend on, add a flush:

```clojure
(before
  (page/clear!)
  (user/install! @frodo)
  (wire/flush))
```

### Multiple Flushes for Effect Chains

Components with `use-effect` that trigger further state changes may need multiple flushes to fully settle:

```clojure
(wire/render [my-component])
(wire/flush)   ;; render + effects
(wire/flush)   ;; re-renders from effect state changes
```

### `act()` Warnings

Wire handles `act()` internally. If you previously set `IS_REACT_ACT_ENVIRONMENT` yourself, remove it. You should not see act warnings from `before`/`after` blocks.

If you need to wrap a custom async callback that triggers a re-render:

```clojure
(wire/act #(invoke-my-callback))
(wire/flush)
```

### `with-let finally` Cleanup

Wire ensures `with-let finally` blocks fire synchronously on unmount. Tests that verify cleanup should work naturally:

```clojure
(it "clears interval on unmount"
  (wire/unmount)
  (wire/flush)
  (should-be empty? (worker/intervals)))
```

### Bucket re-memory and Reagent 2

If your project uses `c3kit.bucket` with `:re-memory` (the default for CLJS), update bucket to a version compatible with Reagent 2.

**Symptoms (before bucket fix):** `db/entity` works but `db/find-by` returns `()`, or CLJC tests fail on CLJS but pass on CLJ.

## Checkbox Migration Patterns

`check-box!` now uses native `click` events instead of `Simulate.change`, so it only fires when the value actually changes.

**Toggle regardless of state:** Use `wire/click!` instead of `wire/check-box!` if you always want the handler to fire.

**Controlled checkboxes with stubbed handlers:** When the handler is stubbed (e.g., `ws/call!`), the component state never updates. React re-renders the checkbox to its previous state, so a second click toggles the same direction. Fix by simulating the server response between clicks:

```clojure
(wire/click! "#-checkbox")
(should-have-invoked-ws :todo/complete (:id @todo))
;; Simulate the server response
(db/tx @todo :complete? true)
(wire/flush)
;; Now the checkbox renders as checked, so clicking unchecks it
(wire/click! "#-checkbox")
(should-have-invoked-ws :todo/uncomplete (:id @todo))
```

**Labels don't forward clicks in tests.** Click the `<input>` directly:

```clojure
;; Won't work — label click doesn't forward in JSDOM
(wire/click! "#-my-label")

;; Works
(wire/click! "#-my-label input")
```

## Blur and Focus Events

`blur!` and `focus!` now dispatch both bubbling and non-bubbling variants (`focusout`/`blur` and `focusin`/`focus`) to match React 18's event delegation. They should work correctly out of the box.

`wire/focus-out!` is available if you only need the bubbling `focusout` event.

## `pushState` SecurityError on `file://` Protocol

If your tests run on `file://` and you use a router that calls `history.pushState` (e.g., `accountant`), React 18's fuller rendering may trigger this error:

```
ERROR: Uncaught SecurityError: Failed to execute 'pushState' on 'History'
```

**Fix:** Call `wire/suppress-history-push-state!` once at the top level of your spec helper:

```clojure
(wire/suppress-history-push-state!)
```

Tests that verify navigation should still stub `accountant/navigate!` explicitly.

## Event Opts Reference

All event functions accept an opts map. Common options:

```clojure
;; Mouse events
(wire/click! "#el" {:shiftKey true :ctrlKey true})
(wire/mouse-down! "#el" {:button 2 :clientX 100 :clientY 200})

;; Keyboard events (4-arity: root, selector, key-code, opts)
(wire/key-down! js/document "#input" wjs/ENTER {:shiftKey true})
(wire/key-down! js/document "#input" wjs/TAB {:metaKey true})

;; Drag events (4-arity: root, selector, data-transfer, opts)
(wire/drag-start! root "#item" {:id 123} {:clientX 50})
```

## Functions Unchanged

These functions work exactly as before:
- `render`, `flush`, `reset!`, `swap!`, `act`
- `select`, `select-all`, `select-map`, `count-all`
- `text`, `text!`, `html`, `html!`, `value`, `class-name`, `tag-name`, `href`, `id`
- `placeholder`, `checked?`, `disabled?`, `readonly?`, `src`, `alt`, `style`
- `with-root-dom`, `with-clean-dom`, `reset-dom!`
- All ajax stub helpers (`stub-ajax`, `last-ajax-*`, `invoke-last-ajax-*`)
- All WebSocket stub helpers (`stub-ws`, `last-ws-*`, `invoke-last-ws-*`)
- All mock helpers (websockets, storage, worker, performance)

---

## Appendix: Troubleshooting

### Tests get slower as the suite progresses

Likely cause: leaked event listeners or undisposed Reagent reactions. Check:

1. Are `with-let finally` blocks firing? Add a `println` in a `finally` block and verify it prints during the test run.
2. Is the functional compiler active? Wire sets this automatically, but check that nothing is overriding it.

### `with-let finally` not firing on unmount

If unmount tests fail (e.g., "expected interval to be cleared"), the cleanup isn't running. This is usually the `queue-cleanup` microtask issue — wire overrides this, so make sure nothing is re-overriding it after wire loads.

### Tests pass individually but fail together

State from one test is leaking into the next. Common causes:
- Reagent atoms not reset between tests
- Document/window event listeners accumulating
- React roots not properly unmounted (use `wire/unmount`)

## Appendix: How Wire's Test Infrastructure Works

This section explains *why* wire's internals work the way they do. You shouldn't need this for a normal migration, but it may help when debugging unusual test failures.

1. **React 18's `act()` is expensive.** It processes all pending work recursively. Using it naively for every flush caused a 4347-test suite to take ~680s.

2. **Wire uses `flushSync` + a single `act(noop)` instead.** `flushSync` synchronously commits renders. A trailing `act(fn [])` drains only deferred `useEffect` callbacks. This brought the same suite down to ~43s.

3. **Functional compiler for `with-let finally`.** The class-based compiler ties `with-let` cleanup to reaction disposal (lazy/GC-dependent). The functional compiler makes cleanup happen via `useEffect`, which React processes on unmount.

4. **Synchronous `queue-cleanup` override.** Reagent's functional compiler defers cleanup via `Promise.resolve().then(...)`. In synchronous JSDOM tests, that microtask never runs. Wire overrides `queue-cleanup` to run disposal immediately.

5. **Listener tracking as safety net.** Wire monkey-patches `addEventListener`/`removeEventListener` on `document` and `window` to track all listeners. On `reset-dom!` (between tests), any remaining listeners are forcefully removed.
