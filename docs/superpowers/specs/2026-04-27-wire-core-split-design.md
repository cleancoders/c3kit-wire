# Wire / Wire-Core Split — Design

**Date:** 2026-04-27
**Status:** Approved (pending implementation plan)

## Problem

`c3kit-wire` currently forces every consumer to pull in `reagent`, `cljsjs/react`, and `cljsjs/react-dom`, even consumers that only use the ajax / rest / websocket / JS-interop layers. Those layers are useful to non-React applications but inaccessible without dragging React along.

We want non-React consumers to be able to use the React-free portions of the library without React on their classpath, while preserving full backward compatibility for existing React consumers — including the Reagent reactivity of public state atoms.

## Constraints

1. **No breaking changes** for existing consumers of `c3kit-wire`. Bumping the version should be the only change required; namespaces, public defs, and behavior all preserved.
2. **Reagent reactivity preserved.** `c3kit.wire.ajax/active-ajax-requests`, `c3kit.wire.rest/active-reqs`, and `c3kit.wire.websocket/open?` remain `reagent/atom`s in the React-flavored artifact, since consumer components rely on this for reactivity.
3. **Avoid duplicated logic.** Shared code lives in one place.
4. **Convenience preserved.** A consumer of the React-flavored coordinate gets React/Reagent transitively, no manual setup required.

## Architecture

### Two artifacts, one repo, lockstep versions

- **`com.cleancoders.c3kit/wire-core`** *(new)* — React-free. No `reagent`, no `cljsjs/react`, no `cljsjs/react-dom` in deps.

  Provides:
  - All `src/clj/c3kit/wire/*.clj` (unchanged).
  - All `src/cljc/c3kit/wire/*.cljc` (unchanged).
  - CLJS namespaces (unchanged): `confetti`, `dragndrop`, `dragndrop2`, `fake-hiccup`, `google`, `js`, `socket`, `util`, `mock.*`.
  - CLJS namespace (refactored, see "Flash decoupling" below): `c3kit.wire.api` — drops its direct `c3kit.wire.flash` require; gains configurable flash callbacks in its `config` atom (default no-ops).
  - CLJS namespaces (new): `c3kit.wire.core.ajax`, `c3kit.wire.core.rest`, `c3kit.wire.core.websocket` — refactored, state-parameterized versions of today's ajax/rest/websocket. Each ships a default `cljs.core/atom`-backed instance for zero-setup non-React use.

- **`com.cleancoders.c3kit/wire`** *(existing coordinate, no consumer-facing change)* — depends on `wire-core` + `reagent` + `cljsjs/react` + `cljsjs/react-dom`.

  Provides:
  - `c3kit.wire.flash` (moved, unchanged).
  - `c3kit.wire.spec_helper` (moved, unchanged).
  - `c3kit.wire.ajax`, `c3kit.wire.rest`, `c3kit.wire.websocket` — thin wrappers that build a state map with `reagent/atom` and delegate to `c3kit.wire.core.*`. Public defs and arities identical to today's source.

Existing consumers' `deps.edn` entries for `c3kit/wire` continue working unchanged; `wire-core` is pulled in transitively without notice.

### Source layout

```
src/
├── clj/c3kit/wire/                  → wire-core jar
│   └── *.clj                          (unchanged)
├── cljc/c3kit/wire/                 → wire-core jar
│   └── *.cljc                         (unchanged)
├── cljs/c3kit/wire/                 → wire-core jar
│   ├── api.cljs                       (refactored: flash decoupled — see below)
│   ├── confetti.cljs, dragndrop.cljs, dragndrop2.cljs,
│   ├── fake_hiccup.cljs, google.cljs, js.cljs, socket.cljs, util.cljs
│   ├── dnd_mobile_patch.js
│   ├── mock/*.cljs
│   └── core/                        ← NEW
│       ├── ajax.cljs                  (was c3kit.wire.ajax, refactored)
│       ├── rest.cljs                  (was c3kit.wire.rest, refactored)
│       └── websocket.cljs             (was c3kit.wire.websocket, refactored)
└── cljs-react/c3kit/wire/           → wire jar (NEW source root)
    ├── ajax.cljs                    ← NEW thin wrapper
    ├── rest.cljs                    ← NEW thin wrapper
    ├── websocket.cljs               ← NEW thin wrapper
    ├── flash.cljs                     (moved from src/cljs/, unchanged)
    └── spec_helper.cljs               (moved from src/cljs/, unchanged)
```

Build paths are physically disjoint: `wire-core` packs `src/clj` + `src/cljc` + `src/cljs`; `wire` packs only `src/cljs-react`. No file filtering, no overlap.

The dev/test classpath includes all four roots — the split happens only at jar-build time.

### Core API: state parameterization

Pattern using `ajax` as the example. Same shape applies to `rest` and `websocket`.

**`c3kit.wire.core.ajax`:**

```clojure
(ns c3kit.wire.core.ajax
  (:require [c3kit.wire.api :as api]
            ...))

;; Pure helpers — no state.
(defn server-down? [{:keys [error-code status]}] ...)
(defn handle-unexpected-response [response ajax-call] ...)

;; State-aware functions take state as first arg.
;; Flash side effects route through (api/config) callbacks — see "Flash decoupling".
(defn handle-server-down [state ajax-call]
  (swap! (:active-requests state) inc)
  ((:flash-add! @api/config) api/server-down-flash)
  ...)
(defn -do-ajax-request [state ajax-call] ...)
(defn ajax! [state method url params handler & opts] ...)

;; Constructor.
(defn make-state
  ([] (make-state cljs.core/atom))
  ([atom-fn] {:active-requests (atom-fn 0)}))

;; Default instance for non-React consumers.
(defonce default-state (make-state))
(def active-ajax-requests (:active-requests default-state))
(defn activity? [] (not= 0 @active-ajax-requests))

;; Convenience functions using default-state.
(defn ajax-default! [method url params handler & opts]
  (apply ajax! default-state method url params handler opts))
```

**`c3kit.wire.ajax` (React wrapper):**

```clojure
(ns c3kit.wire.ajax
  (:require [c3kit.wire.core.ajax :as core]
            [reagent.core :as reagent]))

(defonce -state (core/make-state reagent/atom))

(def active-ajax-requests (:active-requests -state))   ; reagent atom — preserves reactivity
(defn activity? [] (not= 0 @active-ajax-requests))

;; Re-export pure helpers.
(def server-down? core/server-down?)
(def handle-unexpected-response core/handle-unexpected-response)

;; Stateful functions: thread our reagent-backed state through.
(defn handle-server-down [ajax-call] (core/handle-server-down -state ajax-call))
(defn ajax! [method url params handler & opts]
  (apply core/ajax! -state method url params handler opts))
```

**Properties:**
- `@c3kit.wire.ajax/active-ajax-requests` in a Reagent component still gets reagent reactivity (constraint 2).
- `(c3kit.wire.ajax/ajax! ...)` keeps the same arity and name (constraint 1).
- A non-React consumer can call `(c3kit.wire.core.ajax/ajax-default! ...)` against the cljs.core/atom default, or `(core/make-state my-atom-fn)` for full control.

The same shape applies to `rest` (`active-reqs` counter) and `websocket` (`open?` flag — and `websocketc/create`'s existing `:atom-fn` parameter is plumbed through naturally).

**Re-export rule for wrappers:** the wrapper re-exports every non-private public def from the corresponding `core.*` namespace. The test for "public" is "anything a current consumer might `(:require [c3kit.wire.ajax :refer [...]])`" — i.e., everything except names prefixed with `-` or otherwise marked private. This guarantees constraint 1 (existing requires keep working).

### Flash decoupling

`c3kit.wire.api` and `c3kit.wire.ajax` currently call `c3kit.wire.flash/add!`, `flash/remove!`, and `flash/add-error!` directly. Since `flash` requires `reagent`, this would force `api` and `ajax` to live in the React jar — undesirable, since both are central to the library's non-UI surface.

Resolution: add flash callbacks to the existing `api/config` atom (this is the same pattern already used for `:redirect-fn` and `:ajax-on-unsuccessful-response`). Defaults are no-ops, so non-React consumers see no flash side effects unless they wire their own.

```clojure
;; c3kit.wire.api (in wire-core, refactored)
(def config (atom {;; ... existing keys ...
                   :flash-add!       (constantly nil)
                   :flash-add-error! (constantly nil)
                   :flash-remove!    (constantly nil)}))

(defn handle-payload [handler payload]
  (try (handler payload)
       (catch :default e
         (log/error "AJAX handler error")
         (log/error e)
         ((:flash-add-error! @config) "Oh no!  I choked on some data.  Doh!"))))

;; etc — every former (flash/add! ...) becomes ((:flash-add! @config) ...)
```

`c3kit.wire.core.ajax` follows the same pattern for its two flash side effects.

`c3kit.wire.flash` (in `wire`, the React jar) auto-registers its functions with `api/config` at namespace load:

```clojure
;; bottom of c3kit.wire.flash
(c3kit.wire.api/configure!
  :flash-add!       add!
  :flash-add-error! add-error!
  :flash-remove!    remove!)
```

Loading order: a consumer that requires (transitively) any React-flavored namespace will load `c3kit.wire.flash`, which loads `c3kit.wire.api` first (api has no-op defaults), then registers callbacks before any ajax call runs at runtime. The React-jar wrappers (`c3kit.wire.ajax/rest/websocket`) all add `[c3kit.wire.flash]` to their `:require` to ensure registration happens regardless of which wrapper the consumer touches first.

This is the only "wiring" cost incurred by constraint 4 (convenience preserved): one auto-registration block at the bottom of `c3kit.wire.flash`.

### Build mechanics

`tools.build` via the existing `:build` alias. One `build.clj`, two jar tasks:

```clojure
(ns build
  (:require [clojure.tools.build.api :as b]))

(def core-lib  'com.cleancoders.c3kit/wire-core)
(def react-lib 'com.cleancoders.c3kit/wire)
(def version   (slurp "VERSION"))

(defn jar-core [_]
  (let [basis (b/create-basis {:project "deps.edn" :aliases [:core-only]})]
    (b/copy-dir {:src-dirs ["src/clj" "src/cljc" "src/cljs"]
                 :target-dir "target/core/classes"})
    (b/write-pom {:basis basis :lib core-lib :version version
                  :class-dir "target/core/classes"})
    (b/jar {:class-dir "target/core/classes"
            :jar-file (format "target/%s-%s.jar" (name core-lib) version)})))

(defn jar-react [_]
  (let [basis (b/create-basis {:project "deps.edn" :aliases [:react-only]})]
    (b/copy-dir {:src-dirs ["src/cljs-react"]
                 :target-dir "target/react/classes"})
    (b/write-pom {:basis basis :lib react-lib :version version
                  :class-dir "target/react/classes"})
    (b/jar {:class-dir "target/react/classes"
            :jar-file (format "target/%s-%s.jar" (name react-lib) version)})))

(defn jar-all [_] (jar-core nil) (jar-react nil))
```

**`deps.edn` aliases drive each jar's pom:**

- `:core-only` — `:replace-deps` with every current dep EXCEPT `reagent` and `cljsjs/react*`.
- `:react-only` — `:replace-deps` with `c3kit/wire-core` (pinned to current version) + `reagent` + `cljsjs/react` + `cljsjs/react-dom`.

The main `:deps` map remains the union (so dev/test see everything). The duplication between `:deps` and the build-only aliases is a known minor wart — acceptable given dep upgrades touch both spots only at upgrade time.

**Versioning:** lockstep. Single `VERSION` file. `wire`'s pom pins `wire-core` at the exact same version. No version drift possible.

**Release flow:** `clj -T:build jar-all` produces two jars; CI deploys both in one step.

### Testing

**Existing tests** (under `:test` alias) — keep working as-is. Dev classpath includes `src/cljs-react/`, so React-flavored namespaces and Reagent are available. These tests are the regression check for constraint 1 (consumer compatibility).

**New tests** for `c3kit.wire.core.*` namespaces, in `spec/cljs/c3kit/wire/core/`:
- `core.ajax-spec`, `core.rest-spec`, `core.websocket-spec`.
- Verify `(*-default! ...)` works with the default cljs.core/atom-backed state.
- Verify `(make-state my-atom-fn)` produces an instance whose state uses the supplied atom factory.
- These run under the existing `:test` alias.

**Classpath-isolation check** — a new `:test-core` alias with `:replace-deps` mirroring `:core-only` (no reagent, no cljsjs/react*) plus speclj. Runs only the `c3kit.wire.core.*` specs. If a core namespace accidentally requires reagent, this run fails to compile.

**CI:** add a `clj -M:test-core:spec-ci` job alongside the existing `clj -M:test:spec-ci`. Both must pass.

## Migration & rollout

Single PR. Order of operations:

1. Refactor `c3kit.wire.api` to drop its direct flash require; replace `flash/add!`/`flash/remove!`/`flash/add-error!` calls with config-driven callbacks (defaults: no-ops).
2. Refactor `c3kit.wire.ajax/rest/websocket` into `c3kit.wire.core.ajax/rest/websocket` under `src/cljs/c3kit/wire/core/`. State threaded through, `make-state` + default instance + `*-default!` convenience functions. Replace any direct flash calls in ajax with the same config-driven callback pattern. Add `core.*` specs.
3. Move `flash.cljs`, `spec_helper.cljs` from `src/cljs/` to `src/cljs-react/`. Add the auto-registration block at the bottom of `flash.cljs`.
4. Create new thin wrappers `c3kit.wire.ajax/rest/websocket` in `src/cljs-react/`. Reagent-atom state, re-exports, delegations. Each requires `[c3kit.wire.flash]` to ensure callback registration.
5. Update `deps.edn` `:paths` to add `src/cljs-react`. Add `:core-only`, `:react-only`, `:test-core` aliases.
6. Write `build.clj` for both jars.
7. Wire CI: add `clj -M:test-core:spec-ci` job.
8. Bump version, release both jars together.

**Consumer-facing release notes:**
- `c3kit/wire` continues to work exactly as before — no `deps.edn` changes needed, all namespaces and behavior preserved.
- New: `c3kit/wire-core` is now available for projects that don't use React/Reagent. Documented public API for non-React consumers: the `make-state` constructor + the `*-default!` convenience functions in `c3kit.wire.core.*`.

## Out of scope

- Equivalents of `flash`/`spec_helper` for non-React consumers. If demand surfaces later, that's a separate brainstorm.
- Independent versioning of `wire` and `wire-core`. Lockstep is the rule.
- Any deprecation of existing public API.
