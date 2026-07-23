### 4.2.1
 * Security: patched newly-flagged vulnerable transitive dependencies (clj-watson, 4 High). Bumps the existing top-level pins `io.netty/netty-*` 4.1.135.Final → 4.1.136.Final (CVE-2026-44891, -55831, -55833 via redisson's netty-codec), and adds pins `com.fasterxml.jackson.core/jackson-databind` 2.22.1 (CVE-2026-54512, -54513 via redisson), `org.eclipse.jetty/jetty-unixdomain-server` 12.1.11 and `org.eclipse.jetty.ee9.websocket/jetty-ee9-websocket-jetty-server` 12.1.11 (CVE-2026-10051, -6790, -8384 via ring). Top-level wins over transitive, no exclusions. No public API changes. clj-watson now reports 0 vulnerable dependencies.

### 4.2.0
 * Bumps `com.cleancoders.c3kit/apron` 2.5.0 → 3.0.1 (a major upgrade, patching jackson-core CVE-2025-52999 / GHSA-72hv-8253-57qq via apron's own pin). Apron 3.0.0 restructured the schema ref/registry into a `*lexicon*` and removed deprecated fns; wire's only affected call was `schema/messages`, now `schema/message-seq` in `c3kit.wire.apic`. Wire's own public API is unchanged, but consumers get apron 3.x transitively — if you use `c3kit.apron.schema` directly, review apron's 3.0.0 changelog for breaking changes (e.g. `messages` → `message-seq`, `*ref-registry*` → `*lexicon*`).

### 4.1.1
 * Security: patched vulnerable transitive dependencies flagged by clj-watson (1 critical, 6 high, 2 medium). Declares patched versions at the top level (top-level wins over transitive, no exclusions): `org.eclipse.jetty/jetty-server` 12.1.11 (via ring), `io.netty/netty-*` 4.1.135.Final (via redisson), `com.fasterxml.jackson.core/jackson-core` 2.22.1 (via redisson), and `org.bouncycastle/bc*-jdk18on` 1.84 (via buddy-sign); bumps `ring/ring` 1.15.3 → 1.15.5. No public API changes. `:local/root` / `:git/url` consumers inherit the new pins; Maven consumers get them via the published pom.
 * CI now runs security scanning (clj-watson + semgrep, both blocking) alongside the build in a single parallel workflow, with GitHub Action refs pinned to commit SHAs.

### 4.1.0
 * Server-side WebSocket is no longer hard-wired to http-kit. `c3kit.wire.websocketc/create` accepts a `:transport` option — a map of `:open`, `:send!`, and `:close` fns — and `c3kit.wire.api` gains a `:ws-transport` config key that `c3kit.wire.websocket/start` threads through, so an app wiring WebSocket via `websocket/service` can run on another Ring server by calling `(api/configure! :ws-transport ...)` with an adapter. http-kit stays the default; consumers who configure nothing are unaffected.

### 4.0.1
 * `c3kit.wire.core.{ajax,rest}` now expose state-bound consumer functions: `get!`, `post!`, `put!`, `delete!`, and (ajax) `request!` operate on `default-state` and match the arities of their `c3kit.wire.{ajax,rest}` counterparts, so consumers can `(:require [c3kit.wire.core.ajax :as ajax])` and call `ajax/get!`. The state-taking primitives are now `do-get!`, `do-post!`, `do-put!`, `do-delete!`, `do-request!`. The previous `*-default!` variants are removed.

### 4.0.0
 * Adds a second, React-free artifact alongside the existing `wire` jar:
   * `com.cleancoders.c3kit/wire` (existing) — React-flavored. Self-contained: ships everything `wire-core` ships **plus** the Reagent wrappers and React/Reagent deps. Most consumers upgrading from 3.0.0 just bump the version (see breaking changes below for the exceptions).
   * `com.cleancoders.c3kit/wire-core` (new) — React-free. Same `c3kit.wire.*` namespaces minus the Reagent wrappers (`ajax`, `rest`, `websocket`, `flash`, `spec_helper`), plus the new `c3kit.wire.core.{ajax,rest,websocket}` namespaces. No `reagent` / `cljsjs/react*` deps. Pull this if your project doesn't use React.
 * The two artifacts are independent — `wire` does **not** declare `wire-core` as a dependency. Pick one. Pulling both onto the same classpath produces duplicate `c3kit.wire.*` entries.
 * Decouples `c3kit.wire.api` from `c3kit.wire.flash` via configurable callbacks (`:flash-add!`, `:flash-add-error!`, `:flash-remove!` in `api/config`). When you require any React-flavored namespace, `c3kit.wire.flash` auto-registers as the implementation, preserving existing behavior.
 * Internally, `c3kit.wire.ajax/rest/websocket` are now thin Reagent wrappers over `c3kit.wire.core.ajax/rest/websocket`; public defs (`active-ajax-requests`, `active-reqs`, `open?`, all functions) keep their reactivity, names, and arities.
 * **Note for `:local/root` / `:git/url` consumers:** the default `:deps` map in `deps.edn` is now the React-free core dep set; `reagent` and `cljsjs/react*` live on a new `:react` alias. Maven consumers (`:mvn/version`) are unaffected — the published poms still declare the full dep list for whichever artifact you pulled. Projects that depend on `c3kit-wire` via `:local/root` or `:git/url` need to add `:react` to their alias chain (or pin Reagent/React deps themselves) to keep getting the React layer in development.
 * **Breaking changes for consumers who reach into internals** (this is why the major version bumps from 3 to 4):
   * `c3kit.wire.websocket/push-handler` is now a 1-arg delegating fn rather than a multimethod. `(defmethod c3kit.wire.websocket/push-handler ...)` will fail at namespace load time with a `Cannot read properties of undefined (reading 'call')` error. Migrate by extending `c3kit.wire.core.websocket/push-handler` instead — its dispatch fn takes `[state message]`, so the method body needs an extra `_state` arg.
   * `c3kit.wire.websocket/client` is no longer exposed at the wrapper namespace; if you were `set!`-ing it (e.g. in tests), use `c3kit.wire.core.websocket/client`.
   * `with-redefs` / `redefs-around` on internal functions in `c3kit.wire.{ajax,rest,websocket}` (e.g. `triage-response`, `handle-server-down`, `make-call!`, `-do-ajax-request`, `-request!`) no longer intercepts internal calls — wrapper functions delegate to `c3kit.wire.core.{ajax,rest,websocket}` and the call chain runs there. Stub the corresponding `c3kit.wire.core.*` symbol instead.
   * `c3kit.wire.ajax/wrap-catch-api-errors` was renamed to `c3kit.wire.ajax/wrap-catch-ajax-errors` (no alias retained). Update any middleware stack that referenced the old name.

### 3.0.0
 * Upgrades to React 18 and Reagent 2
 * All components now render as React functional components (functional compiler enabled by default)
 * `with-let finally` blocks fire synchronously on unmount
 * Rendering uses `flushSync` for synchronous commits instead of `act()` for performance
 * `wire/reset!` and `wire/swap!` now flush synchronously after updating
 * `wire/unmount` replaces `reagent.dom/unmount-component-at-node` (React 18 `createRoot` API)
 * Event functions (`click!`, `key-down!`, `blur!`, etc.) dispatch native DOM events with optional opts maps
 * `change` is now element-type-aware (text inputs dispatch `input`, selects dispatch `change`, checkboxes dispatch `click`)
 * `blur!`/`focus!` dispatch both bubbling and non-bubbling variants for React 18 event delegation
 * `check-box` uses native `click` events (only fires when value actually changes)
 * Document/window event listeners are tracked and cleaned up between tests
 * Adds `wire/act` for wrapping custom state updates that need effect processing
 * Adds `wire/suppress-history-push-state!` for `file://` protocol test environments
 * Removes `simulator` var, `simulate`/`simulate!`, and `stub-reset-swap`
 * Removes old `mouse-down` 3-arity `[root button selector]` — use opts map instead
 * See docs/migrating-to-3.0.0.md for full upgrade guide

### 2.8.6
 * Fixes bug where default wrap accept header middleware was in the wrong order inside wrap-rest

### 2.8.5
 * wraps accept header by default for REST ns

### 2.8.4
 * Reverts reagent dependency upgrade
   * This will also require an upgrade to react and react-dom
 * Upgrades Apron

### 2.8.3
 * Fixes wrong mime type being sent

### 2.8.2
 * No longer throws when shutting down unconfigured message queue
 * Upgrades dependencies

### 2.8.1
 * Adds middleware support for REST client

### 2.8.0
 * Upgrades apron dependency
 * Adds redisson to dependencies
 * Adds lock namespace with locking functionality
 * Adds In-Memory implementation of Lock protocol
 * Adds Redis implementation of Lock protocol

### 2.7.0
 * Adds c3kit.wire.routes with lazy-routes, wrap-prefix, and redirect-routes.

### 2.6.4
 * Removes usage of deprecated `reagent.dom/dom-node` in Google Reagent component

### 2.6.3
 * Updates error handling approach to no longer override the handler function with another

### 2.6.2
 * Provides error handling for message queues

### 2.6.1
 * Upgrade dependencies
 * Replace Carmine backend of Redis with Lettuce

### 2.6.0
 * Adds `c3kit.wire.message-queue` namespace with in-memory and Redis implementations
 * Upgrades dependencies

### 2.5.3
 * Fixes client side DELETE for RESTful requests

### 2.5.2
 * Fixes bug where AJAX client only recognized 200 response as successful

### 2.5.1
 * Fixes cookies being sent securely over HTTP

### 2.5.0
 * Adds DELETE functionality to REST client

### 2.4.0
 * Added support for more HTTP responses in REST library

### 2.3.3
 * Fixes cookies not being attached to requests properly for REST client

### 2.3.1
 * Adds RESTful API client & server wrappers

### 2.3.0
 * Allow websocket client to specify URI, with query params if needed

### 2.2.0
 * Apron and Scaffold 2.2.0

### 2.1.7
 * Upgrade deps
 * Add In-Memory and Interceptor WebSocket Mocks
 * Remove `log/warn!` side effect from cljs spec-helper

### 2.1.6
 * bump apron

### 2.1.4
 * Upgrades deps
 * Adds `->event`, `dispatch-event`, and `o-merge!` to `c3kit.wire.js`
 * Passes cljs specs under `:advanced` optimizations

### 2.1.2
 * using apron 2.1.3, replace use of deprecated schema fns
 * update from ring/ring 1.10.0 to 1.12.0

### 2.1.8
 * adds `NumpadEnter` to keyboard options
 * adds `ENTER?*` to check for either `ENTER` or `NumpadEnter`

### 2.1.9
 * adds try-catch around `wsc/connection-responder!` for offline cases
