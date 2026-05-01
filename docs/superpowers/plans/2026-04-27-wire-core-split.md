# Wire / Wire-Core Split — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **⚠️ Plan superseded post-implementation (2026-05-01):** The wire jar is now **self-contained** — it bundles all of wire-core's content plus the React wrappers, with no transitive dep on wire-core. See the design doc's Architecture section for the current shape and rationale. Tasks 16, 17 (and parts of others) below describe the original wire-depends-on-wire-core approach; the current `dev/build.clj` and `deps.edn` reflect the revised approach. Treat this plan as historical context for how the split was assembled, not as a description of the current build.

**Goal:** Split `c3kit-wire` into two artifacts (`wire-core` for React-free use, `wire` for React/Reagent consumers) so non-React applications can use ajax/rest/websocket/JS-interop layers without pulling in React.

**Architecture:** Two artifacts published from one repo, lockstep-versioned. `wire-core` ships React-free namespaces under `src/clj`, `src/cljc`, and `src/cljs` (including new `c3kit.wire.core.ajax/rest/websocket`). `wire` is self-contained — it ships everything `wire-core` ships plus the Reagent wrappers under `src/cljs-react/`, and declares Reagent + cljsjs/react alongside the existing JVM-side deps. The two artifacts are independent; neither declares the other as a dep. Existing consumers' `deps.edn` keeps working unchanged. Flash side effects in `c3kit.wire.api` and `c3kit.wire.core.ajax` are decoupled via callbacks in the `api/config` atom; `c3kit.wire.flash` registers itself on namespace load.

**Tech Stack:** Clojure 1.12, ClojureScript 1.12, Reagent 2, Speclj 3.12, tools.build 0.10, deps.edn.

**Spec:** `docs/superpowers/specs/2026-04-27-wire-core-split-design.md`

---

## Phase 1 — Pre-flight

### Task 1: Establish baseline and create the work branch

**Files:** none modified

- [ ] **Step 1: Confirm git status is clean and on master**

  Run: `git status` and `git rev-parse --abbrev-ref HEAD`

  Expected: `working tree clean`, current branch `master`. If dirty or on another branch, stash/commit/checkout before proceeding.

- [ ] **Step 2: Create and check out the work branch**

  Run: `git checkout -b wire-core-split`

  Expected: switched to a new branch `wire-core-split`.

- [ ] **Step 3: Run the full Clojure test suite**

  Run: `clojure -M:test:spec-ci`

  Expected: all tests pass, no failures, no errors.

- [ ] **Step 4: Run the full ClojureScript test suite**

  Run: `clojure -M:test:cljs once`

  Expected: all tests pass.

---

## Phase 2 — Decouple flash from `c3kit.wire.api` (in place)

The goal of this phase is to remove `c3kit.wire.api`'s direct dependency on `c3kit.wire.flash` while keeping every existing test green at every step. We do this by adding callback config keys, registering flash as their implementation, and only then swapping the call sites — the order matters so behavior never changes mid-step.

### Task 2: Add flash callback keys to `api/config`

**Files:**
- Modify: `src/cljs/c3kit/wire/api.cljs`
- Test: `spec/cljs/c3kit/wire/api_spec.cljs` (existing, no edit yet)

- [ ] **Step 1: Write the failing test**

  Add this `it` block inside the existing `(describe "API" ...)` block (after the existing `:on-error option` test):

  ```clojure
  (it "config exposes flash callback slots with no-op defaults"
    (let [c @sut/config]
      (should= (constantly nil) (:flash-add!       c))
      (should= (constantly nil) (:flash-add-error! c))
      (should= (constantly nil) (:flash-remove!    c))))
  ```

  Note: `(constantly nil)` returns a fresh function each call — so `should=` won't actually equal these. Replace with `should-not-be-nil`:

  ```clojure
  (it "config exposes flash callback slots"
    (let [c @sut/config]
      (should-not-be-nil (:flash-add!       c))
      (should-not-be-nil (:flash-add-error! c))
      (should-not-be-nil (:flash-remove!    c))))
  ```

  Add `should-not-be-nil` to the `:require-macros [speclj.core ...]` line in the spec ns if not already present.

- [ ] **Step 2: Run the test to verify it fails**

  Run: `clojure -M:test:cljs once -f c3kit.wire.api-spec`

  (Or run the full cljs suite — the new test will be the failing one.)

  Expected: FAIL — config map does not contain those keys.

- [ ] **Step 3: Add the keys to the config map**

  In `src/cljs/c3kit/wire/api.cljs`, change the `config` def from:

  ```clojure
  (def config (atom {
                     :version                       "undefined"
                     :redirect-fn                   cc/redirect!
                     :ajax-prep-fn                  nil
                     :ajax-on-unsuccessful-response nil
                     :ws-csrf-token                 nil
                     :ws-on-reconnected             nil
                     :ws-uri                        nil
                     :ws-uri-path                   "/user/websocket"
                     }))
  ```

  to:

  ```clojure
  (def config (atom {
                     :version                       "undefined"
                     :redirect-fn                   cc/redirect!
                     :ajax-prep-fn                  nil
                     :ajax-on-unsuccessful-response nil
                     :ws-csrf-token                 nil
                     :ws-on-reconnected             nil
                     :ws-uri                        nil
                     :ws-uri-path                   "/user/websocket"
                     :flash-add!                    (constantly nil)
                     :flash-add-error!              (constantly nil)
                     :flash-remove!                 (constantly nil)
                     }))
  ```

- [ ] **Step 4: Run the test to verify it passes**

  Run: `clojure -M:test:cljs once -f c3kit.wire.api-spec`

  Expected: PASS.

- [ ] **Step 5: Run the full cljs suite to confirm no regressions**

  Run: `clojure -M:test:cljs once`

  Expected: all green.

- [ ] **Step 6: Commit**

  ```bash
  git add src/cljs/c3kit/wire/api.cljs spec/cljs/c3kit/wire/api_spec.cljs
  git commit -m "wire-split: add flash callback slots to api/config"
  ```

### Task 3: Wire flash auto-registration into `c3kit.wire.flash`

At this step, `c3kit.wire.flash` is still in `src/cljs/`. Adding the auto-registration here (before any moves) is safe and lets us swap api's call sites in Task 4 without touching loading order.

**Files:**
- Modify: `src/cljs/c3kit/wire/flash.cljs`
- Test: `spec/cljs/c3kit/wire/flash_spec.cljs` (existing, add a test)

- [ ] **Step 1: Write the failing test**

  Add this test to the bottom of `spec/cljs/c3kit/wire/flash_spec.cljs` inside the existing top-level `describe`:

  ```clojure
  (it "registers itself with c3kit.wire.api on load"
    (let [c @c3kit.wire.api/config]
      (should= sut/add!       (:flash-add!       c))
      (should= sut/add-error! (:flash-add-error! c))
      (should= sut/remove!    (:flash-remove!    c))))
  ```

  Add `[c3kit.wire.api]` to the spec's `:require` if not already there.

- [ ] **Step 2: Run the test to verify it fails**

  Run: `clojure -M:test:cljs once -f c3kit.wire.flash-spec`

  Expected: FAIL — config callbacks are still the no-op defaults from Task 2.

- [ ] **Step 3: Add the auto-registration block at the bottom of `src/cljs/c3kit/wire/flash.cljs`**

  Append the following to the file (after `flash-root`):

  ```clojure
  ;; Side-effecting registration with c3kit.wire.api/config on namespace load.
  ;; This is unusual — most namespaces don't mutate other namespaces' state at load time —
  ;; but it's how we preserve backward compatibility: existing consumers expect that
  ;; requiring any React-flavored namespace (which transitively requires this one) wires up
  ;; flash side effects in api and core.ajax. See docs/superpowers/specs/2026-04-27-wire-core-split-design.md.
  (c3kit.wire.api/configure!
    :flash-add!       add!
    :flash-add-error! add-error!
    :flash-remove!    remove!)
  ```

  Add `[c3kit.wire.api :as api]` to flash's `:require` (if not already present — it isn't) and use the alias instead of the fully-qualified form:

  Update the `(:require ...)` block in flash.cljs from:

  ```clojure
  (:require [c3kit.apron.corec :refer [conjv]]
            [c3kit.apron.log :as log]
            [c3kit.wire.flashc :as flashc]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.util :as util]
            [reagent.core :as reagent]))
  ```

  to:

  ```clojure
  (:require [c3kit.apron.corec :refer [conjv]]
            [c3kit.apron.log :as log]
            [c3kit.wire.api :as api]
            [c3kit.wire.flashc :as flashc]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.util :as util]
            [reagent.core :as reagent]))
  ```

  And rewrite the registration block to use the alias:

  ```clojure
  (api/configure!
    :flash-add!       add!
    :flash-add-error! add-error!
    :flash-remove!    remove!)
  ```

- [ ] **Step 4: Run the test to verify it passes**

  Run: `clojure -M:test:cljs once -f c3kit.wire.flash-spec`

  Expected: PASS.

- [ ] **Step 5: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green.

- [ ] **Step 6: Commit**

  ```bash
  git add src/cljs/c3kit/wire/flash.cljs spec/cljs/c3kit/wire/flash_spec.cljs
  git commit -m "wire-split: flash auto-registers with api/config on namespace load"
  ```

### Task 4: Route api's flash side effects through the config callbacks

**Files:**
- Modify: `src/cljs/c3kit/wire/api.cljs`

- [ ] **Step 1: Replace `flash/add-error!` in `handle-payload`**

  Change:

  ```clojure
  (defn- handle-payload [handler payload]
    (try
      (handler payload)
      (catch :default e
        (log/error "AJAX handler error")
        (log/error e)
        (flash/add-error! "Oh no!  I choked on some data.  Doh!"))))
  ```

  to:

  ```clojure
  (defn- handle-payload [handler payload]
    (try
      (handler payload)
      (catch :default e
        (log/error "AJAX handler error")
        (log/error e)
        ((:flash-add-error! @config) "Oh no!  I choked on some data.  Doh!"))))
  ```

- [ ] **Step 2: Replace `flash/add!` in `new-version!`**

  Change:

  ```clojure
  (defn new-version! [version]
    (log/warn "new version: " version ", was: " (:version @config))
    (flash/add! new-version-flash))
  ```

  to:

  ```clojure
  (defn new-version! [version]
    (log/warn "new version: " version ", was: " (:version @config))
    ((:flash-add! @config) new-version-flash))
  ```

- [ ] **Step 3: Replace `flash/remove!` and `flash/add!` in `handle-api-response`**

  Change the body of `handle-api-response` so the two `flash/...` calls become callback dispatches:

  ```clojure
  (defn handle-api-response [raw-response {:keys [handler options]}]
    ((:flash-remove! @config) server-down-flash)
    (log/trace "raw response: " raw-response)
    (let [{:keys [status flash payload version uri]} (apic/conform-response raw-response)]
      (when (seq flash)
        (run! (:flash-add! @config) flash))
      ;; ... rest unchanged
  ```

  Leave the remainder of `handle-api-response` unchanged.

- [ ] **Step 4: Remove the now-unused `[c3kit.wire.flash :as flash]` from the `:require` block**

  Change the ns form's require list to drop `flash` (keep everything else):

  ```clojure
  (ns c3kit.wire.api
    (:require [c3kit.apron.corec :as ccc]
              [c3kit.apron.log :as log]
              [c3kit.wire.apic :as apic]
              [c3kit.wire.flashc :as flashc]
              [c3kit.wire.js :as cc]))
  ```

- [ ] **Step 5: Run the api spec**

  Run: `clojure -M:test:cljs once -f c3kit.wire.api-spec`

  Expected: PASS — the existing tests that assert `flash/state` contains a flash continue to pass because flash auto-registered in Task 3, so `(:flash-add! @config)` is `flash/add!`, and behavior is identical.

- [ ] **Step 6: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green.

- [ ] **Step 7: Commit**

  ```bash
  git add src/cljs/c3kit/wire/api.cljs
  git commit -m "wire-split: route api's flash side effects through config callbacks"
  ```

---

## Phase 3 — Set up new source root for the React jar

### Task 5: Add `src/cljs-react` path

**Files:**
- Modify: `deps.edn`

- [ ] **Step 1: Create the directory**

  Run: `mkdir -p src/cljs-react/c3kit/wire`

- [ ] **Step 2: Add `src/cljs-react` to the `:test` alias's `:extra-paths`**

  In `deps.edn`, change the `:test` alias's `:extra-paths` from:

  ```clojure
  :extra-paths   ["dev" "spec/clj" "spec/cljc" "spec/cljs"]
  ```

  to:

  ```clojure
  :extra-paths   ["dev" "spec/clj" "spec/cljc" "spec/cljs" "src/cljs-react"]
  ```

  Leave the top-level `:paths` alone for now — we'll add `src/cljs-react` there in a later task once it has files.

- [ ] **Step 3: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green (no behavior change yet — directory is empty).

- [ ] **Step 4: Commit**

  ```bash
  git add deps.edn
  git commit -m "wire-split: add src/cljs-react test path"
  ```

---

## Phase 4 — Extract `c3kit.wire.core.ajax`

The objective: produce a state-parameterized `c3kit.wire.core.ajax` (no top-level reagent atom, configurable via `make-state`), preserve a default cljs.core/atom-backed instance for non-React consumers, and replace `c3kit.wire.ajax` with a thin Reagent-wrapping wrapper in `src/cljs-react/`. Existing `ajax_spec.cljs` is the regression check throughout.

### Task 6: Create `c3kit.wire.core.ajax` with `make-state` (TDD for new API)

**Files:**
- Create: `src/cljs/c3kit/wire/core/ajax.cljs`
- Create: `spec/cljs/c3kit/wire/core/ajax_spec.cljs`

- [ ] **Step 1: Create the directory**

  Run: `mkdir -p src/cljs/c3kit/wire/core spec/cljs/c3kit/wire/core`

- [ ] **Step 2: Write the failing test for `make-state`**

  Create `spec/cljs/c3kit/wire/core/ajax_spec.cljs`:

  ```clojure
  (ns c3kit.wire.core.ajax-spec
    (:require-macros [speclj.core :refer [describe it should= should-not-be-nil]])
    (:require [c3kit.wire.core.ajax :as sut]
              [speclj.core]))

  (describe "core ajax — make-state"

    (it "make-state with no arg uses cljs.core/atom"
      (let [s (sut/make-state)]
        (should-not-be-nil (:active-requests s))
        (should= 0 @(:active-requests s))))

    (it "make-state with custom atom-fn uses that atom-fn"
      (let [calls (atom [])
            my-atom-fn (fn [v] (swap! calls conj v) (atom v))
            s (sut/make-state my-atom-fn)]
        (should= [0] @calls)
        (should= 0 @(:active-requests s)))))
  ```

- [ ] **Step 3: Run the test to verify it fails**

  Run: `clojure -M:test:cljs once -f c3kit.wire.core.ajax-spec`

  Expected: FAIL — namespace `c3kit.wire.core.ajax` does not exist.

- [ ] **Step 4: Create the minimal implementation**

  Create `src/cljs/c3kit/wire/core/ajax.cljs`:

  ```clojure
  (ns c3kit.wire.core.ajax)

  (defn make-state
    ([] (make-state cljs.core/atom))
    ([atom-fn] {:active-requests (atom-fn 0)}))
  ```

- [ ] **Step 5: Run the test to verify it passes**

  Run: `clojure -M:test:cljs once -f c3kit.wire.core.ajax-spec`

  Expected: PASS.

- [ ] **Step 6: Commit**

  ```bash
  git add src/cljs/c3kit/wire/core/ajax.cljs spec/cljs/c3kit/wire/core/ajax_spec.cljs
  git commit -m "wire-split: introduce c3kit.wire.core.ajax/make-state"
  ```

### Task 7: Port the rest of ajax logic into `core.ajax`, threading state through

**Files:**
- Modify: `src/cljs/c3kit/wire/core/ajax.cljs`

The strategy: copy the contents of the existing `src/cljs/c3kit/wire/ajax.cljs` into `core/ajax.cljs`, change the ns to `c3kit.wire.core.ajax`, replace the top-level `(reagent/atom 0)` with `(:active-requests state)` parameterized through the call chain, replace `flash/...` calls with `((:flash-X! @api/config) ...)`, and drop reagent + flash from requires. Add `default-state`, `active-ajax-requests`, `activity?`, and `*-default!` convenience defs at the bottom.

- [ ] **Step 1: Replace the entire contents of `src/cljs/c3kit/wire/core/ajax.cljs` with this:**

  ```clojure
  (ns c3kit.wire.core.ajax
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [c3kit.apron.corec :as ccc]
              [c3kit.apron.log :as log]
              [c3kit.wire.api :as api]
              [c3kit.wire.js :as cc]
              [cljs-http.client :as http]
              [cljs.core.async :as async]
              [clojure.string :as str]))

  (declare -do-ajax-request)

  (defn make-state
    ([] (make-state cljs.core/atom))
    ([atom-fn] {:active-requests (atom-fn 0)}))

  (defn server-down? [{:keys [error-code status]}]
    (and (= :http-error error-code) (#{0 502} status)))

  (defn handle-server-down [state ajax-call]
    (log/warn "Appears that server is down.  Will retry after in a moment.")
    ((:flash-add! @api/config) api/server-down-flash)
    (cc/timeout 3000 #(-do-ajax-request state ajax-call)))

  (defn handle-unexpected-response [response ajax-call]
    (if-let [on-http-error (:on-http-error (:options ajax-call))]
      (on-http-error response)
      (log/error "Unexpected AJAX response: " response ajax-call)))

  (defn handle-unsuccessful-response [state response ajax-call]
    (cond (server-down? response) (handle-server-down state ajax-call)
          (= 403 (:status response)) ((:flash-add! @api/config) api/forbidden-flash)
          :else (handle-unexpected-response response ajax-call)))

  (defn- success? [{:keys [status]}]
    (<= 200 status 299))

  (defn triage-response [state response ajax-call]
    (cond (success? response) (api/handle-api-response (:body response) ajax-call)
          :else (if-let [handler (:ajax-on-unsuccessful-response @api/config)]
                  (handler response ajax-call)
                  (handle-unsuccessful-response state response ajax-call))))

  (defn prep-csrf [header token]
    (fn [ajax-call]
      (assoc-in ajax-call [:options :headers header] token)))

  (defn params-key [ajax-call]
    (if (#{"GET" "HEAD"} (:method ajax-call))
      :query-params
      (case (-> ajax-call :options :params-type)
        nil :transit-params
        :transit :transit-params
        :query :query-params
        :form :form-params
        :edn :edn-params
        :json :json-params
        :multipart :multipart-params)))

  (def pass-through-keys [:accept
                          :basic-auth
                          :content-type
                          :default-headers
                          :headers
                          :method
                          :oauth-token
                          :transit-opts
                          :with-credentials?])

  (defn request-map [ajax-call]
    (let [prep (or (:ajax-prep-fn @api/config) identity)
          {:keys [options params] :as ajax-call} (prep ajax-call)]
      (cond-> (select-keys options pass-through-keys)
              params (assoc (params-key ajax-call) params))))

  (defn -do-ajax-request [state {:keys [method method-fn url params] :as ajax-call}]
    (log/debug "<" method url params)
    (go
      (swap! (:active-requests state) inc)
      (let [request (request-map ajax-call)
            {:keys [error-code status body] :as response} (async/<! (method-fn url request))]
        (log/debug ">" method url error-code status (:status body))
        (triage-response state response ajax-call)
        (swap! (:active-requests state) dec))))

  (defn build-ajax-call [method method-fn url params handler opt-args]
    {:options   (ccc/->options opt-args)
     :method    method
     :method-fn method-fn
     :url       url
     :params    params
     :handler   handler})

  (defn get! [state url params handler & opt-args]
    (-do-ajax-request state (build-ajax-call "GET" http/get url params handler opt-args)))

  (defn post! [state url params handler & opt-args]
    (-do-ajax-request state (build-ajax-call "POST" http/post url params handler opt-args)))

  (defn request! [state method url params handler & opt-args]
    (let [method-name (str/upper-case (name method))
          method-fn (fn [url & [req]] (http/request (merge req {:method method :url url})))]
      (-do-ajax-request state (build-ajax-call method-name method-fn url params handler opt-args))))

  (defonce default-state (make-state))
  (def active-ajax-requests (:active-requests default-state))
  (defn activity? [] (not= 0 @active-ajax-requests))

  (defn get-default!     [url params handler & opts] (apply get!     default-state url params handler opts))
  (defn post-default!    [url params handler & opts] (apply post!    default-state url params handler opts))
  (defn request-default! [m url params handler & opts] (apply request! default-state m url params handler opts))
  ```

- [ ] **Step 2: Run the existing core.ajax-spec to verify the make-state tests still pass**

  Run: `clojure -M:test:cljs once -f c3kit.wire.core.ajax-spec`

  Expected: PASS (the file from Task 6 still works).

- [ ] **Step 3: Run the full cljs suite to confirm no regressions elsewhere**

  Run: `clojure -M:test:cljs once`

  Expected: all green. The existing `c3kit.wire.ajax` is still present in `src/cljs/c3kit/wire/ajax.cljs` — both files coexist for now. The existing `ajax_spec.cljs` is unaffected because it requires `c3kit.wire.ajax`, not `c3kit.wire.core.ajax`.

- [ ] **Step 4: Commit**

  ```bash
  git add src/cljs/c3kit/wire/core/ajax.cljs
  git commit -m "wire-split: port ajax logic into c3kit.wire.core.ajax with state threading"
  ```

### Task 8: Add tests for `core.ajax` activity counter behavior

**Files:**
- Modify: `spec/cljs/c3kit/wire/core/ajax_spec.cljs`

- [ ] **Step 1: Write the failing test**

  Add inside the existing `describe`:

  ```clojure
  (it "activity? reflects default-state's active-requests counter"
    (should= false (sut/activity?))
    (swap! sut/active-ajax-requests inc)
    (should= true (sut/activity?))
    (swap! sut/active-ajax-requests dec)
    (should= false (sut/activity?)))

  (it "default-state shares its active-requests atom with the active-ajax-requests def"
    (should= sut/active-ajax-requests (:active-requests sut/default-state)))
  ```

- [ ] **Step 2: Run the test to verify it passes**

  Run: `clojure -M:test:cljs once -f c3kit.wire.core.ajax-spec`

  Expected: PASS — the implementation already supports these properties; this is documenting the invariants.

  If they don't pass, investigate before proceeding — something in Task 7 went wrong.

- [ ] **Step 3: Commit**

  ```bash
  git add spec/cljs/c3kit/wire/core/ajax_spec.cljs
  git commit -m "wire-split: cover core.ajax default-state and activity?"
  ```

### Task 9: Replace `c3kit.wire.ajax` with a thin Reagent wrapper

**Files:**
- Delete: `src/cljs/c3kit/wire/ajax.cljs`
- Create: `src/cljs-react/c3kit/wire/ajax.cljs`

- [ ] **Step 1: Delete the old file and create the wrapper in the same commit**

  Delete `src/cljs/c3kit/wire/ajax.cljs`:

  Run: `git rm src/cljs/c3kit/wire/ajax.cljs`

  Create `src/cljs-react/c3kit/wire/ajax.cljs` with this content:

  ```clojure
  (ns c3kit.wire.ajax
    (:require [c3kit.wire.core.ajax :as core]
              [c3kit.wire.flash]
              [reagent.core :as reagent]))

  (defonce -state (core/make-state reagent/atom))

  (def active-ajax-requests (:active-requests -state))
  (defn activity? [] (not= 0 @active-ajax-requests))

  (def server-down?                 core/server-down?)
  (def handle-unexpected-response   core/handle-unexpected-response)
  (def prep-csrf                    core/prep-csrf)
  (def params-key                   core/params-key)
  (def pass-through-keys            core/pass-through-keys)
  (def request-map                  core/request-map)
  (def build-ajax-call              core/build-ajax-call)

  (defn handle-server-down [ajax-call]
    (core/handle-server-down -state ajax-call))

  (defn handle-unsuccessful-response [response ajax-call]
    (core/handle-unsuccessful-response -state response ajax-call))

  (defn triage-response [response ajax-call]
    (core/triage-response -state response ajax-call))

  (defn -do-ajax-request [ajax-call]
    (core/-do-ajax-request -state ajax-call))

  (defn get!     [url params handler & opts] (apply core/get!     -state url params handler opts))
  (defn post!    [url params handler & opts] (apply core/post!    -state url params handler opts))
  (defn request! [m url params handler & opts] (apply core/request! -state m url params handler opts))
  ```

  The `[c3kit.wire.flash]` require ensures flash auto-registration runs when consumers require this namespace, even if they never touch flash directly.

- [ ] **Step 2: Run the existing ajax-spec to verify behavior is preserved**

  Run: `clojure -M:test:cljs once -f c3kit.wire.ajax-spec`

  Expected: PASS — the spec was written against the same public API surface. `active-ajax-requests` is now a `reagent/atom` (provided by `make-state reagent/atom`), preserving reactivity.

- [ ] **Step 3: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green.

- [ ] **Step 4: Commit**

  ```bash
  git add -A src/cljs/c3kit/wire/ajax.cljs src/cljs-react/c3kit/wire/ajax.cljs
  git commit -m "wire-split: replace c3kit.wire.ajax with reagent wrapper over core.ajax"
  ```

---

## Phase 5 — Extract `c3kit.wire.core.rest`

Same shape as Phase 4. `rest.cljs` doesn't call flash directly, so this is a pure state-threading refactor.

### Task 10: Create `c3kit.wire.core.rest` with `make-state` (TDD)

**Files:**
- Create: `src/cljs/c3kit/wire/core/rest.cljs`
- Create: `spec/cljs/c3kit/wire/core/rest_spec.cljs`

- [ ] **Step 1: Write the failing test**

  Create `spec/cljs/c3kit/wire/core/rest_spec.cljs`:

  ```clojure
  (ns c3kit.wire.core.rest-spec
    (:require-macros [speclj.core :refer [describe it should= should-not-be-nil]])
    (:require [c3kit.wire.core.rest :as sut]
              [speclj.core]))

  (describe "core rest — make-state"

    (it "make-state with no arg uses cljs.core/atom"
      (let [s (sut/make-state)]
        (should-not-be-nil (:active-requests s))
        (should= 0 @(:active-requests s))))

    (it "default-state shares active-reqs"
      (should= sut/active-reqs (:active-requests sut/default-state))))
  ```

- [ ] **Step 2: Run the test to verify it fails**

  Run: `clojure -M:test:cljs once -f c3kit.wire.core.rest-spec`

  Expected: FAIL — namespace doesn't exist.

- [ ] **Step 3: Create `src/cljs/c3kit/wire/core/rest.cljs`**

  ```clojure
  (ns c3kit.wire.core.rest
    (:require [c3kit.apron.corec :as ccc]
              [c3kit.wire.api :as api]
              [c3kit.wire.restc :as restc]
              [cljs-http.client :as client]
              [cljs.core.async :refer-macros [go]]
              [cljs.core.async :as async]))

  (defn make-state
    ([] (make-state cljs.core/atom))
    ([atom-fn] {:active-requests (atom-fn 0)}))

  (defn configure! [& options]
    (swap! api/config merge (ccc/->options options)))

  (defn success?         [response] (<= 200 (:status response) 299))
  (defn error?           [response] (<= 400 (:status response) 600))
  (defn bad-req?         [response] (= 400 (:status response)))
  (defn unauthenticated? [response] (= 401 (:status response)))
  (defn unauthorized?    [response] (= 403 (:status response)))
  (defn not-found?       [response] (= 404 (:status response)))
  (defn server-error?    [response] (<= 500 (:status response)))

  (defn payload [opts response]
    (if (:rest/unwrap-body? opts)
      (:body response)
      response))

  (defn wrap-success-handler [handler]
    (fn [opts response]
      (when (success? response)
        (handler opts (payload opts response)))))

  (defn wrap-response-code [status f handler]
    (fn [opts response]
      (if (= status (:status response))
        (f (payload opts response))
        (handler opts response))))

  (defn wrap-response-codes [spec handler]
    (reduce-kv
      (fn [acc k v]
        (wrap-response-code k v acc))
      handler
      spec))

  (defn wrap-user-handlers [handler]
    (fn [opts response]
      (if-let [callback (get-in opts [:rest/handlers (:status response)])]
        (callback (payload opts response))
        (handler opts response))))

  (defn wrap-form-errors [handler]
    (fn [opts response]
      (let [ratom (:rest/form-ratom opts)
            errors (:errors (:body response))]
        (when (and ratom errors)
          (swap! ratom assoc :errors errors :display-errors? true)))
      (handler opts response)))

  (defn with-handlers [& opts]
    (let [spec (ccc/->options opts)]
      {:rest/handlers spec}))

  (defn wrap-handler [middleware handler opts]
    (letfn [(callback [_opts response]
              (handler response))]
      (partial (middleware callback) opts)))

  (defn -request! [state channel callback]
    (go
      (swap! (:active-requests state) inc)
      (callback (async/<! channel))
      (swap! (:active-requests state) dec))
    nil)

  (defn request! [state method url request handler options]
    (let [opts       (merge @api/config (ccc/->options options))
          middleware (:rest/response-middleware opts)
          callback   (if middleware (wrap-handler middleware handler opts) handler)
          channel    (method url (restc/-maybe-update-req request))]
      (-request! state channel callback)))

  (defn get!    [state url request callback & opts] (request! state client/get    url request callback opts))
  (defn post!   [state url request callback & opts] (request! state client/post   url request callback opts))
  (defn put!    [state url request callback & opts] (request! state client/put    url request callback opts))
  (defn delete! [state url request callback & opts] (request! state client/delete url request callback opts))

  (defonce default-state (make-state))
  (def active-reqs (:active-requests default-state))
  (defn activity? [] (not= 0 @active-reqs))

  (defn get-default!    [url request callback & opts] (apply get!    default-state url request callback opts))
  (defn post-default!   [url request callback & opts] (apply post!   default-state url request callback opts))
  (defn put-default!    [url request callback & opts] (apply put!    default-state url request callback opts))
  (defn delete-default! [url request callback & opts] (apply delete! default-state url request callback opts))
  ```

- [ ] **Step 4: Run the test to verify it passes**

  Run: `clojure -M:test:cljs once -f c3kit.wire.core.rest-spec`

  Expected: PASS.

- [ ] **Step 5: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green. Existing `c3kit.wire.rest` is still in `src/cljs/`, so existing rest_spec still passes.

- [ ] **Step 6: Commit**

  ```bash
  git add src/cljs/c3kit/wire/core/rest.cljs spec/cljs/c3kit/wire/core/rest_spec.cljs
  git commit -m "wire-split: port rest logic into c3kit.wire.core.rest"
  ```

### Task 11: Replace `c3kit.wire.rest` with a thin wrapper

**Files:**
- Delete: `src/cljs/c3kit/wire/rest.cljs`
- Create: `src/cljs-react/c3kit/wire/rest.cljs`

- [ ] **Step 1: Delete and replace**

  Run: `git rm src/cljs/c3kit/wire/rest.cljs`

  Create `src/cljs-react/c3kit/wire/rest.cljs`:

  ```clojure
  (ns c3kit.wire.rest
    (:require [c3kit.wire.core.rest :as core]
              [c3kit.wire.flash]
              [reagent.core :as reagent]))

  (defonce -state (core/make-state reagent/atom))

  (def active-reqs (:active-requests -state))
  (defn activity? [] (not= 0 @active-reqs))

  (def configure!            core/configure!)
  (def success?              core/success?)
  (def error?                core/error?)
  (def bad-req?              core/bad-req?)
  (def unauthenticated?      core/unauthenticated?)
  (def unauthorized?         core/unauthorized?)
  (def not-found?            core/not-found?)
  (def server-error?         core/server-error?)
  (def payload               core/payload)
  (def wrap-success-handler  core/wrap-success-handler)
  (def wrap-response-code    core/wrap-response-code)
  (def wrap-response-codes   core/wrap-response-codes)
  (def wrap-user-handlers    core/wrap-user-handlers)
  (def wrap-form-errors      core/wrap-form-errors)
  (def with-handlers         core/with-handlers)
  (def wrap-handler          core/wrap-handler)

  (defn -request! [channel callback] (core/-request! -state channel callback))
  (defn request!  [method url request handler options] (core/request! -state method url request handler options))
  (defn get!      [url request callback & opts] (apply core/get!    -state url request callback opts))
  (defn post!     [url request callback & opts] (apply core/post!   -state url request callback opts))
  (defn put!      [url request callback & opts] (apply core/put!    -state url request callback opts))
  (defn delete!   [url request callback & opts] (apply core/delete! -state url request callback opts))
  ```

- [ ] **Step 2: Run the existing rest-spec**

  Run: `clojure -M:test:cljs once -f c3kit.wire.rest-spec`

  Expected: PASS.

- [ ] **Step 3: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green.

- [ ] **Step 4: Commit**

  ```bash
  git add -A src/cljs/c3kit/wire/rest.cljs src/cljs-react/c3kit/wire/rest.cljs
  git commit -m "wire-split: replace c3kit.wire.rest with reagent wrapper over core.rest"
  ```

---

## Phase 6 — Extract `c3kit.wire.core.websocket`

Websocket has React UI components (`disconnected-button`, `connection-status`) at the bottom of the file — those go in the wrapper, not in core.

### Task 12: Create `c3kit.wire.core.websocket` (TDD for `make-state`)

**Files:**
- Create: `src/cljs/c3kit/wire/core/websocket.cljs`
- Create: `spec/cljs/c3kit/wire/core/websocket_spec.cljs`

- [ ] **Step 1: Write the failing test**

  Create `spec/cljs/c3kit/wire/core/websocket_spec.cljs`:

  ```clojure
  (ns c3kit.wire.core.websocket-spec
    (:require-macros [speclj.core :refer [describe it should= should-not-be-nil]])
    (:require [c3kit.wire.core.websocket :as sut]
              [speclj.core]))

  (describe "core websocket — make-state"

    (it "make-state with no arg uses cljs.core/atom"
      (let [s (sut/make-state)]
        (should-not-be-nil (:open? s))
        (should= false @(:open? s))))

    (it "default-state shares open?"
      (should= sut/open? (:open? sut/default-state))))
  ```

- [ ] **Step 2: Run the test to verify it fails**

  Run: `clojure -M:test:cljs once -f c3kit.wire.core.websocket-spec`

  Expected: FAIL — namespace doesn't exist.

- [ ] **Step 3: Create `src/cljs/c3kit/wire/core/websocket.cljs`**

  ```clojure
  (ns c3kit.wire.core.websocket
    (:require [c3kit.apron.corec :as ccc]
              [c3kit.apron.log :as log]
              [c3kit.wire.api :as api]
              [c3kit.wire.js :as wjs]
              [c3kit.wire.websocketc :as wsc]
              [clojure.string :as str]))

  (defn make-state
    ([] (make-state cljs.core/atom))
    ([atom-fn] {:open?         (atom-fn false)
                :reconnection? (atom false)
                :pending-calls (atom [])}))

  (def client nil)

  (declare connect!)

  (defn handle-remote-response [remote-call response]
    (log/debug "remote response: " response)
    (api/handle-api-response response remote-call))

  (defn build-remote-call [kind params handler opt-args]
    {:options (ccc/->options opt-args)
     :kind    kind
     :params  params :handler handler})

  (defn make-call! [{:keys [kind params] :as remote-call}]
    (log/debug "call: " kind params)
    (wsc/call! client kind params (partial handle-remote-response remote-call)))

  (defn call! [state kind params handler & opt-args]
    (let [remote-call (build-remote-call kind params handler opt-args)]
      (if @(:open? state)
        (make-call! remote-call)
        (swap! (:pending-calls state) conj remote-call))))

  (defn on-open [state _]
    (let [calls @(:pending-calls state)]
      (reset! (:pending-calls state) [])
      (doseq [call calls]
        (make-call! call))))

  (defmulti push-handler (fn [_state message] (:kind message)))

  (defmethod push-handler :ws/ping [_ _])

  (defmethod push-handler :default [_ message]
    (log/warn "Unhandled push event: " message))

  (defmethod push-handler :ws/hello [_ {:keys [params]}]
    (log/debug "hello: " params))

  (defmethod push-handler :ws/open [state _]
    (reset! (:open? state) true)
    (when @(:reconnection? state)
      (reset! (:reconnection? state) false)
      (when-let [on-reconnected (:ws-on-reconnected @api/config)]
        (on-reconnected)))
    (let [calls @(:pending-calls state)]
      (reset! (:pending-calls state) [])
      (doseq [call calls]
        (make-call! call))))

  (defmethod push-handler :ws/close [state _]
    (reset! (:open? state) false)
    (reset! (:reconnection? state) true)
    (log/warn "connection closed... reconnecting")
    (wjs/timeout 1000 (connect! @api/config)))

  (defmethod push-handler :ws/error [_ _] (log/warn "websocket error"))

  (defn message-handler [state message]
    (push-handler state message))

  (defn- build-local-uri [path]
    (let [location (wjs/page-location)
          protocol (if (= "https:" (ccc/oget location "protocol")) "wss:" "ws:")
          host     (ccc/oget location "host")]
      (str protocol "//" host path)))

  (defn- build-connection-uri [{:keys [ws-uri ws-uri-path ws-csrf-token]} connection-id]
    (let [uri (or ws-uri (build-local-uri ws-uri-path))]
      (str uri
           (if (str/includes? uri "?") "&" "?")
           "connection-id=" connection-id
           "&ws-csrf-token=" ws-csrf-token)))

  (defn connect! [config]
    (let [connection-id (str (ccc/new-uuid))
          uri           (build-connection-uri config connection-id)]
      (wsc/connect! client uri (:ws-csrf-token config) connection-id)))

  (defn start! [state atom-fn]
    (when-not client
      (if (:ws-csrf-token @api/config)
        (do (set! client (wsc/create (partial message-handler state) :atom-fn atom-fn))
            (connect! @api/config))
        (log/error "CSRF Token missing.  Unable to start websocket."))))

  (defn stop! []
    (log/info "stopping websocket"))

  (defonce default-state (make-state))
  (def open? (:open? default-state))

  (defn call-default! [kind params handler & opt-args]
    (apply call! default-state kind params handler opt-args))

  (defn start-default! [] (start! default-state cljs.core/atom))
  ```

- [ ] **Step 4: Run the test to verify it passes**

  Run: `clojure -M:test:cljs once -f c3kit.wire.core.websocket-spec`

  Expected: PASS.

- [ ] **Step 5: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green. Existing websocket_spec is still exercising `c3kit.wire.websocket` (still in `src/cljs/`).

- [ ] **Step 6: Commit**

  ```bash
  git add src/cljs/c3kit/wire/core/websocket.cljs spec/cljs/c3kit/wire/core/websocket_spec.cljs
  git commit -m "wire-split: port websocket logic into c3kit.wire.core.websocket"
  ```

### Task 13: Replace `c3kit.wire.websocket` with a Reagent wrapper that keeps the React UI components

**Files:**
- Delete: `src/cljs/c3kit/wire/websocket.cljs`
- Create: `src/cljs-react/c3kit/wire/websocket.cljs`

- [ ] **Step 1: Delete and replace**

  Run: `git rm src/cljs/c3kit/wire/websocket.cljs`

  Create `src/cljs-react/c3kit/wire/websocket.cljs`:

  ```clojure
  (ns c3kit.wire.websocket
    (:require [c3kit.apron.log :as log]
              [c3kit.wire.core.websocket :as core]
              [c3kit.wire.flash]
              [c3kit.wire.js :as wjs]
              [reagent.core :as reagent]))

  (defonce -state (core/make-state reagent/atom))

  (def open? (:open? -state))

  (def handle-remote-response core/handle-remote-response)
  (def build-remote-call      core/build-remote-call)
  (def make-call!             core/make-call!)
  (def message-handler        core/message-handler)
  (def connect!               core/connect!)
  (def stop!                  core/stop!)
  (def push-handler           core/push-handler)

  (defn call! [kind params handler & opt-args]
    (apply core/call! -state kind params handler opt-args))

  (defn on-open [_] (core/on-open -state nil))

  (defn start! [] (core/start! -state reagent/atom))

  (defn disconnected-button []
    (let [open? (reagent/atom false)]
      (fn []
        [:div.contextual-menu-anchor
         [:button#-disconnected-button.disconnected.naked {:on-click #(reset! open? true)}
          [:span.fas.fa-exclamation-triangle.animation.error.small-margin-left]]
         (when @open?
           [:div#-disconnected-menu-overlay.contextual-menu {:on-click #(reset! open? false)}
            [:div#-disconnected-menu.card
             [:h5.small-margin-bottom [:span.fas.fa-link] "Connection Broken"]
             [:p.margin-bottom "Your connection with the server has been broken. "
              "We are trying to reconnect.  If that doesn't seem to help, please try reloading this page."]
             [:button.primary {:on-click wjs/page-reload!} "Reload Page"]]])])))

  (defn connection-status [] (when-not @open? [disconnected-button]))
  ```

- [ ] **Step 2: Run the existing websocket-spec**

  Run: `clojure -M:test:cljs once -f c3kit.wire.websocket-spec`

  Expected: PASS.

- [ ] **Step 3: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green.

- [ ] **Step 4: Commit**

  ```bash
  git add -A src/cljs/c3kit/wire/websocket.cljs src/cljs-react/c3kit/wire/websocket.cljs
  git commit -m "wire-split: replace c3kit.wire.websocket with reagent wrapper over core.websocket"
  ```

---

## Phase 7 — Move `flash` and `spec_helper` to the React source root

### Task 14: Move flash.cljs

**Files:**
- Move: `src/cljs/c3kit/wire/flash.cljs` → `src/cljs-react/c3kit/wire/flash.cljs`

- [ ] **Step 1: Move the file**

  Run: `git mv src/cljs/c3kit/wire/flash.cljs src/cljs-react/c3kit/wire/flash.cljs`

- [ ] **Step 2: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green. The dev/test classpath includes `src/cljs-react/`, so the namespace resolves identically.

- [ ] **Step 3: Commit**

  ```bash
  git add -A src/cljs/c3kit/wire/flash.cljs src/cljs-react/c3kit/wire/flash.cljs
  git commit -m "wire-split: move flash.cljs to src/cljs-react"
  ```

### Task 15: Move spec_helper.cljs

**Files:**
- Move: `src/cljs/c3kit/wire/spec_helper.cljs` → `src/cljs-react/c3kit/wire/spec_helper.cljs`

- [ ] **Step 1: Move the file**

  Run: `git mv src/cljs/c3kit/wire/spec_helper.cljs src/cljs-react/c3kit/wire/spec_helper.cljs`

- [ ] **Step 2: Run the full cljs suite**

  Run: `clojure -M:test:cljs once`

  Expected: all green.

- [ ] **Step 3: Commit**

  ```bash
  git add -A src/cljs/c3kit/wire/spec_helper.cljs src/cljs-react/c3kit/wire/spec_helper.cljs
  git commit -m "wire-split: move spec_helper.cljs to src/cljs-react"
  ```

---

## Phase 8 — Build script & deps aliases for two-jar publishing

### Task 16: Add `:core-only` and `:react-only` aliases to deps.edn

**Files:**
- Modify: `deps.edn`

- [ ] **Step 1: Add the aliases**

  Inside the `:aliases` map in `deps.edn`, add:

  ```clojure
  :core-only  {:replace-deps {buddy/buddy-sign                 {:mvn/version "3.6.1-359"}
                              cljs-http/cljs-http              {:mvn/version "0.1.49"}
                              com.cleancoders.c3kit/apron      {:mvn/version "2.5.0"}
                              compojure/compojure              {:mvn/version "1.7.2" :exclusions [ring/ring-core ring/ring-codec]}
                              http-kit/http-kit                {:mvn/version "2.8.1"}
                              io.lettuce/lettuce-core          {:mvn/version "6.8.1.RELEASE"}
                              org.apache.commons/commons-pool2 {:mvn/version "2.12.1"}
                              org.clojure/clojure              {:mvn/version "1.12.4"}
                              org.clojure/core.async           {:mvn/version "1.8.741"}
                              org.redisson/redisson            {:mvn/version "3.52.0"}
                              ring/ring                        {:mvn/version "1.15.3"}
                              ring/ring-anti-forgery           {:mvn/version "1.4.0"}}}

  :react-only {:replace-deps {cljsjs/react     {:mvn/version "18.3.1-1"}
                              cljsjs/react-dom {:mvn/version "18.3.1-1"}
                              reagent/reagent  {:mvn/version "2.0.1"}}}
  ```

  Note `:react-only` does NOT list `wire-core` as a dep — `build.clj` adds it via `:extra` at jar time so the version always matches the contents of `VERSION` without us having to keep a hard-coded version string in the alias in sync.

- [ ] **Step 2: Verify the aliases parse**

  Run: `clojure -A:core-only -Stree | head -20`

  Expected: deps tree prints, no errors. (We're not building yet — just confirming the alias is structurally valid.)

  Run: `clojure -A:react-only -Stree | head -20`

  Expected: deps tree prints (showing reagent + cljsjs/react + cljsjs/react-dom). `wire-core` is intentionally absent — it gets injected by `build.clj` at jar time.

- [ ] **Step 3: Commit**

  ```bash
  git add deps.edn
  git commit -m "wire-split: add :core-only and :react-only build aliases"
  ```

### Task 17: Replace `dev/build.clj` with a two-jar build script

**Files:**
- Modify: `dev/build.clj`

The existing `dev/build.clj` builds a single jar. Replace it with a version that produces both `wire-core` and `wire` jars from the same repo, lockstep-versioned, with `wire`'s pom pinning `wire-core` at the exact version.

- [ ] **Step 1: Replace the contents of `dev/build.clj`**

  ```clojure
  (ns build
    (:require [cemerick.pomegranate.aether :as aether]
              [clojure.java.shell :as shell]
              [clojure.string :as str]
              [clojure.tools.build.api :as b]))

  (def group-name "com.cleancoders.c3kit")
  (def core-lib  (symbol group-name "wire-core"))
  (def react-lib (symbol group-name "wire"))
  (def version (str/trim (slurp "VERSION")))

  (def core-class-dir  "target/core/classes")
  (def react-class-dir "target/react/classes")
  (def core-jar-file   (format "target/wire-core-%s.jar" version))
  (def react-jar-file  (format "target/wire-%s.jar" version))

  (def pom-template
    [[:licenses
      [:license
       [:name "MIT License"]
       [:url "https://github.com/cleancoders/c3kit-wire/blob/master/LICENSE"]]]])

  (defn clean [_]
    (println "cleaning")
    (b/delete {:path "target"}))

  (defn- core-basis []
    (b/create-basis {:project "deps.edn" :aliases [:core-only]}))

  (defn- react-basis []
    ;; Inject wire-core at the current VERSION via :extra so the alias itself
    ;; doesn't have to keep a hard-coded version string in sync with VERSION.
    (b/create-basis {:project "deps.edn"
                     :aliases [:react-only]
                     :extra   {:deps {core-lib {:mvn/version version}}}}))

  (defn jar-core [_]
    (println "building" core-jar-file)
    (let [basis (core-basis)]
      (b/copy-dir {:src-dirs   ["src/clj" "src/cljc" "src/cljs"]
                   :target-dir core-class-dir})
      (b/write-pom {:basis     basis
                    :class-dir core-class-dir
                    :lib       core-lib
                    :version   version
                    :pom-data  pom-template})
      (b/jar {:class-dir core-class-dir
              :jar-file  core-jar-file})))

  (defn jar-react [_]
    (println "building" react-jar-file)
    (let [basis (react-basis)]
      (b/copy-dir {:src-dirs   ["src/cljs-react"]
                   :target-dir react-class-dir})
      (b/write-pom {:basis     basis
                    :class-dir react-class-dir
                    :lib       react-lib
                    :version   version
                    :pom-data  pom-template})
      (b/jar {:class-dir react-class-dir
              :jar-file  react-jar-file})))

  (defn- deploy-config [lib jar-file class-dir]
    {:coordinates       [lib version]
     :jar-file          jar-file
     :pom-file          (str/join "/" [class-dir "META-INF/maven" group-name (name lib) "pom.xml"])
     :repository        {"clojars" {:url      "https://clojars.org/repo"
                                    :username (System/getenv "CLOJARS_USERNAME")
                                    :password (System/getenv "CLOJARS_PASSWORD")}}
     :transfer-listener :stdout})

  (defn jar [_]
    (clean nil)
    (jar-core nil)
    ;; Install wire-core to local Maven so jar-react's basis can resolve it.
    ;; This is a side effect of building, intentional: the wire jar's pom must
    ;; declare wire-core as a dep at the same version, and tools.deps insists on
    ;; resolving every declared dep. Local install is the simplest way to make
    ;; that resolution succeed without round-tripping through Clojars.
    (println "installing wire-core" version "to local Maven (build prerequisite)")
    (aether/install (deploy-config core-lib core-jar-file core-class-dir))
    (jar-react nil))

  (defn tag [_]
    (let [clean? (str/blank? (:out (shell/sh "git" "diff")))
          tags   (delay (->> (shell/sh "git" "tag") :out str/split-lines set))]
      (cond (not clean?) (do (println "ABORT: commit master before tagging") (System/exit 1))
            (contains? @tags version) (println "tag already exists")
            :else (do (println "pushing tag" version)
                      (shell/sh "git" "tag" version)
                      (shell/sh "git" "push" "--tags")))))

  (defn install [_]
    ;; (jar nil) already installs wire-core to local Maven as a build prerequisite,
    ;; so we only install the wire jar here.
    (jar nil)
    (println "installing wire" version)
    (aether/install (deploy-config react-lib react-jar-file react-class-dir)))

  (defn deploy [_]
    (tag nil)
    (jar nil)
    (println "deploying wire-core" version)
    (aether/deploy (deploy-config core-lib core-jar-file core-class-dir))
    (println "deploying wire" version)
    (aether/deploy (deploy-config react-lib react-jar-file react-class-dir)))
  ```

- [ ] **Step 2: Test the local build**

  Run: `clojure -T:build clean`

  Expected: `cleaning`, `target/` removed.

  Run: `clojure -T:build jar`

  Expected: console prints `building target/wire-core-3.0.0.jar` and `building target/wire-3.0.0.jar`. Two jar files exist in `target/`. No errors.

- [ ] **Step 3: Inspect the wire-core jar's contents**

  Run: `unzip -l target/wire-core-3.0.0.jar | grep cljs | head -20`

  Expected: lists `c3kit/wire/core/ajax.cljs`, `c3kit/wire/core/rest.cljs`, `c3kit/wire/core/websocket.cljs`, plus `c3kit/wire/api.cljs`, `js.cljs`, etc.

  Run: `unzip -l target/wire-core-3.0.0.jar | grep flash`

  Expected: only `c3kit/wire/flashc.cljc` and `c3kit/wire/flash.clj` (server-side). NO `c3kit/wire/flash.cljs`.

- [ ] **Step 4: Inspect the wire jar's contents**

  Run: `unzip -l target/wire-3.0.0.jar | grep cljs`

  Expected: only `c3kit/wire/ajax.cljs`, `rest.cljs`, `websocket.cljs`, `flash.cljs`, `spec_helper.cljs`. Nothing else.

- [ ] **Step 5: Inspect the wire jar's pom for the wire-core dependency**

  Run: `unzip -p target/wire-3.0.0.jar META-INF/maven/com.cleancoders.c3kit/wire/pom.xml | grep -A 1 wire-core`

  Expected: `<artifactId>wire-core</artifactId>` followed by `<version>3.0.0</version>`.

- [ ] **Step 6: Inspect the wire-core pom — confirm no reagent/cljsjs**

  Run: `unzip -p target/wire-core-3.0.0.jar META-INF/maven/com.cleancoders.c3kit/wire-core/pom.xml | grep -E "reagent|cljsjs"`

  Expected: no output (no matches).

- [ ] **Step 7: Commit**

  ```bash
  git add dev/build.clj
  git commit -m "wire-split: build script produces wire-core and wire jars"
  ```

---

## Phase 9 — Classpath-isolation test for wire-core

### Task 18: Add `:test-core` alias

**Files:**
- Modify: `deps.edn`

- [ ] **Step 1: Add the alias**

  Inside the `:aliases` map in `deps.edn`, add:

  ```clojure
  :test-core {:replace-deps {buddy/buddy-sign                        {:mvn/version "3.6.1-359"}
                             cljs-http/cljs-http                     {:mvn/version "0.1.49"}
                             com.cleancoders.c3kit/apron             {:mvn/version "2.5.0"}
                             compojure/compojure                     {:mvn/version "1.7.2" :exclusions [ring/ring-core ring/ring-codec]}
                             http-kit/http-kit                       {:mvn/version "2.8.1"}
                             io.lettuce/lettuce-core                 {:mvn/version "6.8.1.RELEASE"}
                             org.apache.commons/commons-pool2        {:mvn/version "2.12.1"}
                             org.clojure/clojure                     {:mvn/version "1.12.4"}
                             org.clojure/clojurescript               {:mvn/version "1.12.134"}
                             org.clojure/core.async                  {:mvn/version "1.8.741"}
                             org.redisson/redisson                   {:mvn/version "3.52.0"}
                             ring/ring                               {:mvn/version "1.15.3"}
                             ring/ring-anti-forgery                  {:mvn/version "1.4.0"}
                             speclj/speclj                           {:mvn/version "3.12.0"}
                             com.cleancoders.c3kit/scaffold          {:mvn/version "2.3.2"}}
              :extra-paths  ["src/clj" "src/cljc" "src/cljs" "spec/cljs/c3kit/wire/core"]}
  ```

  Notice this alias deliberately omits `reagent`, `cljsjs/react`, `cljsjs/react-dom`, AND `src/cljs-react/`. If a `core.*` namespace accidentally requires reagent, the cljs compiler will fail.

- [ ] **Step 2: Run the core-only spec subset**

  Run: `clojure -M:test-core:cljs once`

  Expected: the three `c3kit.wire.core.*-spec` files compile and pass. If you see compilation errors mentioning `reagent`, `react`, or `flash`, that's a real bug — a `core.*` namespace has an unintended require.

- [ ] **Step 3: Commit**

  ```bash
  git add deps.edn
  git commit -m "wire-split: add :test-core classpath isolation alias"
  ```

### Task 19: Wire `:test-core` into CI

**Files:**
- Modify: `.github/workflows/test.yml`

- [ ] **Step 1: Add a `:test-core` step**

  After the existing "Run ClojureScript Tests" step (which runs `clojure -M:test:cljs once`), add:

  ```yaml
        - name: Run wire-core Classpath Isolation Tests
          run: clojure -M:test-core:cljs once
  ```

  The full `steps:` list should now end with three test steps in order: Run Clojure Tests → Run ClojureScript Tests → Run wire-core Classpath Isolation Tests.

- [ ] **Step 2: Commit**

  ```bash
  git add .github/workflows/test.yml
  git commit -m "wire-split: wire :test-core classpath isolation into CI"
  ```

---

## Phase 10 — Update top-level `:paths` and finalize

### Task 20: Add `src/cljs-react` to top-level `:paths`

**Files:**
- Modify: `deps.edn`

Until this task, `src/cljs-react` was on the test classpath only. The library jar build doesn't read `:paths` — it copies explicit dirs in `dev/build.clj`. But the dev REPL and any consumer who depends on this repo as a `:local/root` reads `:paths`. Adding `src/cljs-react` here keeps dev workflows consistent.

- [ ] **Step 1: Update `:paths`**

  In `deps.edn`, change:

  ```clojure
  :paths   ["src/clj" "src/cljc" "src/cljs"]
  ```

  to:

  ```clojure
  :paths   ["src/clj" "src/cljc" "src/cljs" "src/cljs-react"]
  ```

- [ ] **Step 2: Remove `src/cljs-react` from `:test :extra-paths`**

  It's now in `:paths` so test inheriting from the project gets it for free. Change the `:test` alias's `:extra-paths` from:

  ```clojure
  :extra-paths   ["dev" "spec/clj" "spec/cljc" "spec/cljs" "src/cljs-react"]
  ```

  back to:

  ```clojure
  :extra-paths   ["dev" "spec/clj" "spec/cljc" "spec/cljs"]
  ```

- [ ] **Step 3: Run the full cljs suite to verify nothing broke**

  Run: `clojure -M:test:cljs once`

  Expected: all green.

- [ ] **Step 4: Run the core-only suite to confirm classpath isolation still holds**

  Run: `clojure -M:test-core:cljs once`

  Expected: all green. (`:test-core` uses `:replace-deps` and explicit `:extra-paths`, so the top-level `:paths` change is irrelevant to it — but verify anyway.)

- [ ] **Step 5: Commit**

  ```bash
  git add deps.edn
  git commit -m "wire-split: include src/cljs-react in top-level :paths"
  ```

### Task 21: Update CHANGES.md

**Files:**
- Modify: `CHANGES.md`

- [ ] **Step 1: Read the current top-of-file**

  Open `CHANGES.md`. The latest entry is `### 3.0.0`. We're shipping the next minor version — `3.1.0` — since constraint A says no breaking changes for existing consumers.

- [ ] **Step 2: Insert a new entry above `### 3.0.0`**

  Add at the top of the file (above `### 3.0.0`):

  ```markdown
  ### 3.1.0
   * Splits the library into two artifacts:
     * `com.cleancoders.c3kit/wire` (existing) — React-flavored, includes Reagent, cljsjs/react, cljsjs/react-dom. No consumer changes required; behavior preserved.
     * `com.cleancoders.c3kit/wire-core` (new) — React-free. Provides ajax/rest/websocket and JS-interop layers under `c3kit.wire.core.*` for projects that don't use Reagent.
   * Decouples `c3kit.wire.api` from `c3kit.wire.flash` via configurable callbacks (`:flash-add!`, `:flash-add-error!`, `:flash-remove!` in `api/config`). When you require any React-flavored namespace, `c3kit.wire.flash` auto-registers as the implementation, preserving existing behavior.
   * Internally, `c3kit.wire.ajax/rest/websocket` are now thin Reagent wrappers over `c3kit.wire.core.ajax/rest/websocket`; public defs (`active-ajax-requests`, `active-reqs`, `open?`, all functions) keep their reactivity, names, and arities.
  ```

- [ ] **Step 3: Update the `VERSION` file**

  Replace contents of `VERSION` with:

  ```
  3.1.0
  ```

  (Single line, no trailing whitespace beyond a single newline.)

- [ ] **Step 4: Commit**

  ```bash
  git add CHANGES.md VERSION
  git commit -m "wire-split: bump to 3.1.0, document the split in CHANGES.md"
  ```

### Task 22: Final smoke test

**Files:** none modified

- [ ] **Step 1: Clean rebuild of both jars**

  Run: `clojure -T:build clean && clojure -T:build jar`

  Expected: both jars build, no errors.

- [ ] **Step 2: Verify wire-core jar pom has no reagent/react**

  Run: `unzip -p target/wire-core-3.1.0.jar META-INF/maven/com.cleancoders.c3kit/wire-core/pom.xml | grep -E "reagent|cljsjs"`

  Expected: no output.

- [ ] **Step 3: Verify wire jar pom pins wire-core at 3.1.0 and includes reagent + cljsjs**

  Run: `unzip -p target/wire-3.1.0.jar META-INF/maven/com.cleancoders.c3kit/wire/pom.xml`

  Expected: dependencies block lists `wire-core` version `3.1.0`, `reagent`, `cljsjs/react`, `cljsjs/react-dom`. Nothing else.

- [ ] **Step 4: Run all three test suites one final time**

  Run all three sequentially:

  ```bash
  clojure -M:test:spec-ci
  clojure -M:test:cljs once
  clojure -M:test-core:cljs once
  ```

  Expected: all green.

- [ ] **Step 5: Verify git log is clean and tells the story**

  Run: `git log --oneline master..HEAD`

  Expected: ~21 commits with `wire-split:` prefix, ordered as we worked.

- [ ] **Step 6: No commit needed; this is verification only**

---

## Done

The branch is now ready for review. The PR description should reference `docs/superpowers/specs/2026-04-27-wire-core-split-design.md` so reviewers can read the design before the diff.
