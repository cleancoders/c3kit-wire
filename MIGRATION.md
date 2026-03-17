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

### 5. `change` is now element-type-aware

`change` detects the element type and dispatches the appropriate event:

- **Text/textarea inputs**: sets value via native prototype setter, dispatches `input` event
- **`<select>` elements**: sets value via native setter, dispatches `change` event
- **Checkbox/radio inputs**: dispatches `click` event (only when value differs)
- **File inputs**: unchanged (sets `files` via `Object.defineProperty`, dispatches `change`)

**Impact:** If you have non-React event listeners on `change` events for text inputs, they won't fire — use the DOM `input` event instead. For `<select>` elements, the `change` event is dispatched as before.

### 6. `wire/unmount` replaces `reagent.dom/unmount-component-at-node`

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

### `with-redefs` and `wire/render` Timing

React 18's `wire/render` uses double-`act()` to flush both Reagent and React batches. If you use `with-redefs` inside a test body and call `wire/render` within it, the second `act()` pass may trigger a re-render AFTER `with-redefs` has restored the original bindings.

**Symptoms:** Test stubs `time/now` or other functions, renders a component, but the component sees the real (unstubbed) value.

**Workaround:** Use `wire/unmount` then `wire/render` inside the `with-redefs` scope. The unmount forces React 18 to discard the existing component tree, and the fresh render picks up the stubbed bindings:

```clojure
(with-redefs [time/now #(time/parse "yyyy-MM-dd" "2021-02-01")]
  (wire/unmount)
  (wire/render [my-component])
  (should-contain "2021" (wire/html "#-year")))
```

`wire/flush` alone won't work because non-reactive function calls (like `time/now`) don't trigger Reagent re-renders.

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

### Pattern: Select/deselect all in multi-select

If a "Select All" checkbox is controlled by React and your test calls `(wire/check-box! "#select-all" true)` but the checkbox is already checked (React rendered it as checked), the click won't fire. Solutions:

1. Use `wire/click!` directly to always toggle
2. Ensure the test starts from a known unchecked state
3. Check the current state before deciding to click

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

## Blur Events and `focusout`

React 18 delegates `onBlur` via the `focusout` event (which bubbles) rather than `blur` (which doesn't bubble). Wire's `wire/blur!` dispatches a `FocusEvent "blur"` with `bubbles: false`, which may not reach React's root listener.

If `wire/blur!` doesn't trigger your `onBlur` handler, dispatch `focusout` directly:

```clojure
;; If wire/blur! doesn't trigger onBlur:
(wire/act #(.dispatchEvent (wire/select "#my-input")
            (js/FocusEvent. "focusout" #js {:bubbles true})))
(wire/flush)
```

This is a known limitation. A future wire update may address this by dispatching `focusout` alongside `blur`.

## Form-2 Components and Modal Re-install

Reagent form-2 components (outer `let` + inner `fn`) persist their local state (atoms created in the `let`) across React 18 re-renders. When a modal is closed and re-opened with `modal/install!`, React 18 may reuse the existing component instance rather than remounting, so the ratom retains values from the previous test.

Fix: add `wire/flush` between `modal/close!` and `modal/install!` to ensure React fully unmounts the component before remounting:

```clojure
(modal/close!)
(wire/flush)          ;; Force React to unmount the form-2 component
(modal/install! :my-modal :key value)
(wire/flush)          ;; Ensure the new instance renders
```

If the component reads from the DB, ensure the DB entity has the correct state before re-opening.

## `component-did-update` Lifecycle in React 18

React 18 may skip `component-did-update` when re-rendering with identical props. If a test renders the same component twice and expects the update lifecycle to fire, pass a different prop to force the update:

```clojure
;; First render
(wire/render [my-component {:value "a"}])

;; Second render — must differ from first to trigger component-did-update
(wire/render [my-component {:value "b"}])
```

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
