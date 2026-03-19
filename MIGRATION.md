# Migrating to Wire with React 18 / Reagent 2

This guide covers migrating consumer projects from the previous wire (React 16 + Reagent 1) to the new wire (React 18 + Reagent 2).

## Architecture Rationale

Understanding *why* wire's test infrastructure works the way it does will help diagnose issues in consumer projects.

### The Problem Chain

1. **React 18's `act()`** processes all pending work recursively (renders → effects → state changes → re-renders → ...). A naive double-`act()` per flush caused catastrophic slowdown in large test suites.

2. **The fix: `flushSync` + single `act(noop)`**. `react-dom/flushSync` synchronously commits renders without recursive draining. A trailing `act(fn [])` processes only deferred `useEffect` callbacks. This cut test time from ~680s to ~43s for a 4347-test suite.

3. **But `with-let finally` didn't fire.** Reagent's class-based compiler ties `with-let` cleanup to reaction disposal, which is lazy/GC-dependent. Components would unmount but their `finally` blocks (which remove event listeners, clear intervals, dispose reactions) never ran.

4. **The fix: functional compiler**. Setting `{:function-components true}` makes all components render as React functional components. `with-let finally` becomes a `useEffect` cleanup, which React processes during unmount.

5. **But `useEffect` cleanup was STILL deferred.** Reagent's `queue-cleanup` in the functional compiler uses `Promise.resolve().then(...)` — a microtask. In synchronous JSDOM test execution, microtasks from Promise don't run between synchronous operations. So cleanup was scheduled but never executed.

6. **The fix: synchronous `queue-cleanup` override.** Wire overrides `reagent.impl.component/queue-cleanup` to run disposal immediately instead of deferring to a microtask. This is the critical piece — without it, every `with-let finally` block in the codebase silently leaks.

7. **Safety net: listener tracking.** Even with proper cleanup, any bug that prevents a `finally` block from running would leak document/window event listeners. Wire intercepts `addEventListener`/`removeEventListener` on `document` and `window`, tracking all listeners. On `reset-dom!` (between tests), any remaining tracked listeners are forcefully removed. This prevents progressive slowdown from listener accumulation.

### Key Diagnostic: Progressive Slowdown

If tests get slower as the suite progresses (e.g., individual `describe` blocks run fast when focused but crawl in the full suite), the likely cause is **leaked event listeners or undisposed Reagent reactions**. Check:

1. Are `with-let finally` blocks actually firing? Add a `println` in a `finally` block and verify it prints during the test run, not at process shutdown.
2. Is `queue-cleanup` being overridden? The override must run before any components render.
3. Is the functional compiler active? Without it, `with-let` cleanup uses class lifecycle methods that may not fire in React 18's JSDOM environment.

### Key Diagnostic: `with-let finally` Not Firing

If unmount tests fail (e.g., "expected interval to be cleared"), the `finally` block isn't executing during unmount. The root cause is almost always the `queue-cleanup` microtask issue. Verify the override is in place and runs synchronously.

### Key Diagnostic: Tests Pass Individually but Fail Together

This usually means test isolation is broken — state from one test leaks into the next. Common causes:
- Module-level Reagent atoms that aren't reset between tests
- Document/window event listeners that accumulate
- React roots that aren't properly unmounted

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

Wire 3.0 handles these React 18 concerns internally — **no changes needed in consumer test code** for these:

- **Functional compiler**: Wire sets `{:function-components true}` so `with-let finally` blocks fire properly via `useEffect` cleanup
- **Synchronous cleanup**: Wire overrides Reagent's deferred `queue-cleanup` to run synchronously, so event listeners and reactions are cleaned up between tests
- **Listener tracking**: Wire tracks all `document`/`window` event listeners and removes leaked ones between tests
- **`act()` scoping**: `IS_REACT_ACT_ENVIRONMENT` is managed internally — `true` only during `act()` calls
- **Synchronous rendering**: `flushSync` ensures renders commit immediately

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

## React 18 Test Migration Guide

Beyond wire's API changes, React 18 introduces behavioral changes that affect tests.

### The Core Issue: Automatic Batching

React 18 batches ALL state updates by default, not just those inside event handlers. This means:

- State updates in `before` blocks don't immediately cause re-renders
- Multiple `reset!` / `swap!` calls batch into a single render
- Components may not have rendered when you expect them to

### Use `wire/reset!` and `wire/swap!` for Reagent Atoms

Wire provides flush-wrapped versions of `reset!` and `swap!`. Use these instead of `clojure.core/reset!` and `clojure.core/swap!` when modifying Reagent atoms that affect rendered components:

```clojure
;; Before (may not flush in React 18)
(reset! my-atom new-value)

;; After (flushes synchronously)
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

### Multiple Flushes for Effect Chains

Components with `use-effect` that trigger state changes may need multiple `wire/flush` calls to fully settle. If a single flush isn't enough (component renders but the effect-triggered re-render hasn't happened), add another flush:

```clojure
(wire/render [my-component])
(wire/flush)   ;; First: render + effects
(wire/flush)   ;; Second: re-renders from effect state changes
```

This is common with components that compute derived state in `use-effect`.

### `act()` Warnings

Wire scopes `IS_REACT_ACT_ENVIRONMENT` to `act()` calls only — it's `false` by default and set to `true` inside each `act()` invocation. This means:

- **You should NOT see act warnings from `before`/`after` blocks.** State updates outside `act()` are expected in test setup and no longer produce warnings.
- **You WILL see act warnings if `IS_REACT_ACT_ENVIRONMENT` is set to `true` globally.** If you previously set this flag yourself, remove it — wire handles it internally.
- Wire's `render`, `flush`, `reset!`, `swap!`, and all event functions handle `act()` internally, so all rendering-related state updates are properly wrapped.

If you do need to wrap a custom state update in `act()` (e.g., simulating an async callback that triggers a re-render), use `wire/act` directly:

```clojure
(wire/act #(invoke-my-callback))
(wire/flush)
```

### `with-let finally` Cleanup

Wire ensures `with-let finally` blocks fire synchronously on unmount. This means:

- Event listeners registered in `with-let` are properly removed
- Intervals/timeouts started in `with-let` are properly cleared
- Reagent reactions are properly disposed

Tests that verify cleanup behavior (e.g., checking that an interval is cleared after unmount) should work naturally:

```clojure
(it "clears interval on unmount"
  (wire/unmount)
  (wire/flush)
  (should-be empty? (worker/intervals)))
```

No special handling is needed — write tests in a behavioral style, agnostic to whether cleanup happens via `useEffect` or `with-let finally`.

### Bucket re-memory and Reagent 2

If your project uses `c3kit.bucket` with `:re-memory` (the default for CLJS), update bucket to a version compatible with Reagent 2. The main issue was `r/cursor` over function-based selectors returning stale/empty results outside reactive context.

**Symptoms (before bucket fix):**
- `db/entity` works but `db/find-by` returns `()`
- CLJC "Common" tests (pure data logic) fail on CLJS but pass on CLJ

**Resolution:** Update to bucket version with Reagent 2 compatible re-memory.

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

### Pattern: Controlled checkbox with stubbed handlers

When a checkbox's `onChange` fires but the handler is stubbed (e.g., `ws/call!` is stubbed), the component state never updates. React re-renders the checkbox to its previous state. A second `wire/click!` sees the same state and toggles the same direction again.

Fix: simulate the server response by updating the DB between clicks:

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

### Pattern: Clicking labels vs inputs for checkboxes

Native `click` on a `<label>` element does NOT forward to the associated `<input>` in test environments the way browsers do. Click the `<input>` element directly:

```clojure
;; May not work — label click doesn't forward in tests
(wire/click! "#-my-label")

;; Works — click the input directly
(wire/click! "#-my-label input")
```

## Blur and Focus Events

Wire's `blur!` and `focus!` now dispatch both the bubbling and non-bubbling variants to match real browser behavior and React 18's event delegation:

- `blur!` dispatches `focusout` (bubbles) then `blur` (doesn't bubble)
- `focus!` dispatches `focusin` (bubbles) then `focus` (doesn't bubble)

React 18 delegates `onBlur` via `focusout` and `onFocus` via `focusin`. This means `wire/blur!` and `wire/focus!` work correctly out of the box — no special handling needed.

For cases where you only need the bubbling event, `wire/focus-out!` is also available:

```clojure
(wire/focus-out! "#my-input")  ;; dispatches only focusout (bubbles)
```

## `pushState` SecurityError on `file://` Protocol

React 18's full component rendering may trigger navigation side effects that were invisible in React 16 (because components didn't fully render with incomplete data). If your project uses `accountant` (or any router that calls `history.pushState`), and tests run on `file://` protocol, you'll see:

```
ERROR: Uncaught SecurityError: Failed to execute 'pushState' on 'History'
```

**Fix:** Call `wire/suppress-history-push-state!` once at the top level of your spec helper:

```clojure
(ns my-app.spec-helper
  (:require [c3kit.wire.spec-helper :as wire]))

(wire/suppress-history-push-state!)
```

This patches `goog.history.Html5History.prototype.setToken` to catch and suppress the error. The navigation intent still runs but the actual URL change is silently skipped on `file://`. Tests that need to verify navigation should still stub `accountant/navigate!` explicitly.

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
