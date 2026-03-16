# Migrating to Wire with React 18 / Reagent 2

This guide covers migrating consumer projects from the previous wire (React 16 + Reagent 1) to the new wire (React 18 + Reagent 2).

## Dependency Updates

Update your `deps.edn`:

```clojure
;; These come transitively through wire, but if you pin them:
cljsjs/react       {:mvn/version "18.3.1-1"}
cljsjs/react-dom   {:mvn/version "18.3.1-1"}
reagent/reagent     {:mvn/version "2.0.1"}
```

Remove any explicit dependency on `cljsjs/react-dom-test-utils` — it is no longer used.

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

### 3. `mouse-down` signature normalized

The old 3-arity `[root button selector]` is removed. Use opts map instead:

```clojure
;; Before
(wire/mouse-down root 1 "#button")

;; After
(wire/mouse-down! root "#button" {:button 1})
```

### 4. `check-box` uses click events

`check-box` now dispatches a native `click` event instead of `Simulate.change`. It only dispatches when the desired value differs from the current DOM state. This matches real browser behavior where clicking a checkbox toggles its state.

**Impact:** Tests that call `(wire/check-box! selector true)` on an already-checked checkbox will be a no-op. If your test relies on the onChange handler firing regardless of current state, you may need to adjust.

### 5. `change` dispatches `input` event

For text inputs, `change` now dispatches a native `input` event (not `change`). React 18's `onChange` handler listens for the DOM `input` event on text inputs. The value is set via the native prototype setter to bypass React's internal value tracker.

**Impact:** If you have non-React event listeners on `change` events specifically, they won't fire. Use the DOM `input` event instead.

## React 18 Test Migration Guide

Beyond wire's API changes, React 18 introduces behavioral changes that affect tests.

### The Core Issue: Automatic Batching

React 18 batches ALL state updates by default, not just those inside event handlers. This means:

- State updates in `before` blocks don't immediately cause re-renders
- Multiple `reset!` / `swap!` calls batch into a single render
- Components may not have rendered when you expect them to

### Use `wire/reset!` and `wire/swap!` for Reagent Atoms

Wire provides `act()`-wrapped versions of `reset!` and `swap!`. Use these instead of `clojure.core/reset!` and `clojure.core/swap!` when modifying Reagent atoms that affect rendered components:

```clojure
;; Before (may not flush in React 18)
(reset! my-atom new-value)

;; After (wrapped in act, flushes synchronously)
(wire/reset! my-atom new-value)
```

### Add `wire/flush` After State Changes in Setup

If your `before` block sets up state that components depend on, add a `wire/flush` after:

```clojure
(before
  (page/clear!)
  (user/install! @frodo)
  (wire/flush))  ;; Ensure components re-render with new state
```

### `act()` Warnings

If you see `"An update to %s inside a test was not wrapped in act(...)"`, it means a state update happened outside of `act()`. Common sources:

1. **`before` blocks** that call `reset!` on Reagent atoms — use `wire/reset!` or add `wire/flush`
2. **WebSocket handlers** that update state — stub them or wrap the invocation in `wire/act`
3. **Async callbacks** — wrap the callback invocation in `(wire/act #(callback))`

### Bucket re-memory and Reagent 2

If your project uses `c3kit.bucket` with `:re-memory` (the default for CLJS), be aware that `db/find-by` queries may return empty results in some cases. This is a known issue with `r/cursor` behavior in Reagent 2 when called outside of a reactive context.

**Symptoms:**
- `db/entity` works but `db/find-by` returns `()`
- CLJC "Common" tests (pure data logic) fail on CLJS but pass on CLJ
- Tests that passed individually may fail in suite runs

**Workaround:** This requires a bucket update for Reagent 2 compatibility. Track this separately from wire migration.

## Checkbox/Select Migration Patterns

The native `click` event for checkboxes behaves differently from `Simulate.change`:

### Pattern: Toggle checkbox regardless of state

```clojure
;; Before: always fired onChange
(wire/check-box! "#my-checkbox" true)

;; After: only fires if not already checked
;; If you need to ensure the handler fires:
(wire/click! "#my-checkbox")  ;; Always toggles
```

### Pattern: Select/deselect all in multi-select

If a "Select All" checkbox is controlled by React and your test calls `(wire/check-box! "#select-all" true)` but the checkbox is already checked (React rendered it as checked), the click won't fire. Solutions:

1. Use `wire/click!` directly to always toggle
2. Ensure the test starts from a known unchecked state
3. Check the current state before deciding to click

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
