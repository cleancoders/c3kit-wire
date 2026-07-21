# Service Worker Library — Design

Date: 2026-06-24
Status: Approved (design)

Quality bar: this is an **open-source** library. It must be **secure by default**,
**SOLID**, and built **test-first (TDD)**. The sections below treat those as hard
constraints, not aspirations.

## Purpose

Provide a reusable, high-quality service-worker library for `c3kit.wire` that gives
consuming apps **offline read** capability: precache an app shell and serve cached
content via configurable per-route caching strategies. Scope is caching/offline only.
Background sync of dirty entities is **out of scope** — `c3kit.bucket` already provides
the IndexedDB layer (`idb-io`, `idb-reader`, `idbc`) and any entity-sync pipeline belongs
to the application, not this library.

No new dependencies. The library stays `apron`-only and never references `c3kit.bucket`.

## Non-Goals

- Background sync / entity push-on-reconnect (app concern; bucket-coupled).
- IndexedDB access (bucket owns it).
- Building/compiling the SW script (app owns its compile target → `/service-worker.js`).
- A SW-specific cache-versioning scheme (rides on the app's existing version; see Cache Naming).

## Architecture

Three namespaces, split strictly by JavaScript execution scope. The ServiceWorker global
scope and the window scope are different runtimes; code for one must not be pulled into the
other's bundle.

| Namespace | Scope | Responsibility |
|---|---|---|
| `c3kit.wire.service-worker.core` | `ServiceWorkerGlobalScope` | strategies, route registry, lifecycle handlers, precache |
| `c3kit.wire.service-worker.register` | window / page | register the SW, drive the update lifecycle |
| `c3kit.wire.service-worker.fake` | test (shipped in `src/cljs`) | in-memory `FakeCacheStorage`/`FakeCache`/fake-fetch for behavior tests |

The fakes ship in `src/` (not `spec/`) so apps that build their own SW config get them for
free — mirroring how wire already ships `spec_helper.cljs` in `src`.

## Dependency Injection (the test seam)

Every handler receives an explicit **context** map. No global reads inside logic, no
passthrough stub namespace (wilson's `js-core` smell), no `set!`-ing globals in tests.

```clojure
{:caches js/caches   ; injectable; defaults to the real global
 :fetch  js/fetch
 :scope  js/self}
```

Tests pass `service-worker-fake` doubles in `ctx`. Assertions are about observable
behavior — what ended up cached, what `Response` came back — not which wrapper was called.

## Async Style

Native promise interop via a tiny internal helper (`then`, `catch`, `resolved`). SW APIs
(`respondWith`, `waitUntil`, `caches.*`, `fetch`) are promise-native, so handlers return
real `Promise`s with no conversion. No custom promise macro (wilson reimplemented one).

## Caching Strategies

The standard five. Each is a **constructor** returning a handler `(fn [ctx request] -> Promise<Response>)`:

```clojure
(cache-first            {:cache "images" :fallback resp-or-fn})
(network-first          {:cache "api"    :fallback resp-or-fn})
(stale-while-revalidate {:cache "pages"})
(network-only           {})
(cache-only             {:cache "shell"  :fallback resp-or-fn})
```

`opts`:
- `:cache` — cache name (string). App names it; may embed the app version for deploy invalidation.
- `:fallback` — optional `Response` or `(fn [request] -> Response)`. Default: a 503
  "Service Unavailable" `Response`.
- `:allow-cross-origin` — opt in to caching cross-origin responses (default false; see Security).
- `:cache-credentialed` — opt in to caching credentialed/auth responses (default false; see Security).

All caching in every strategy is gated by the shared `cacheable?` predicate (Security), so the
secure-by-default policy holds regardless of which strategy is chosen.

Strategy semantics:
- **cache-first** — return cached if present; else fetch, cache a clone, return; on network
  failure return `:fallback`.
- **network-first** — fetch, cache a clone, return; on network failure return cached, else `:fallback`.
- **stale-while-revalidate** — return cached immediately (if present) while fetching in the
  background to refresh the cache; if no cache, await fetch; on total failure return `:fallback`.
- **network-only** — fetch passthrough; no caching; on failure `:fallback`.
- **cache-only** — return cached; if absent return `:fallback`; never hits network.

Only successful responses are cached (e.g. `response.ok`); error/opaque-where-inappropriate
responses are not written, to avoid poisoning the cache.

## Route Registry (predicates, first-match-wins)

```clojure
(register-route! matcher strategy)  ; matcher = predicate fn | regex | exact-path string
(set-default!   strategy)           ; fallthrough handler; default = network passthrough
(precache!      ["/" "/css/app.css" ...] cache-name) ; addAll into the named shell cache on install
(reset-routes!)                     ; clears the registry (test/setup convenience)
```

- Matchers are normalized to predicates: a regex matches against the request URL/path;
  a string matches the path exactly; a fn receives the `request`.
- Routes are held in an atom of `[{:match pred :handler strategy}]`. First match wins.
- Fetch with no matching route uses the default (network passthrough unless overridden).

## Lifecycle Wiring

```clojure
(start! ctx)  ; wires install/activate/fetch listeners on (:scope ctx) via wjs/add-listener
```

- **install** — precache the shell list (`cache.addAll`) inside `waitUntil`; call `skipWaiting`.
- **activate** — purge every cache **not** referenced by the current precache cache + route
  caches (open/closed cleanup, OCP-friendly); call `clients.claim`; wrapped in `waitUntil`.
- **fetch** — `GET` → first matching route's strategy (`respondWith`); non-`GET` → network
  passthrough.

The consuming app writes its own `service_worker.cljs` with a `-main` that calls
`precache!` / `register-route!` / `set-default!` then `start!`. Wire ships the namespaces;
the app owns the cljs compile target that emits `/service-worker.js`.

### Known-cache set for purge

`activate` computes the keep-set as: the precache cache name ∪ every `:cache` named by a
registered route ∪ the default strategy's cache (if any). Any other cache name is deleted.
This is how deploy invalidation works without a SW-specific version: when the app bakes its
version into cache names, last deploy's names fall outside the new keep-set and are purged.

## Page Registration

```clojure
(register! {:url "/service-worker.js" :on-update f :on-active f}) ; -> Promise<registration>
(unregister!)                                                     ; -> Promise
```

- `register!` registers the SW, wires `updatefound` → new worker `statechange`, and invokes
  `:on-update` (new worker installed, update available) and `:on-active` (worker activated)
  callbacks. Surfacing real callbacks fixes wilson's log-only gap.
- No-ops gracefully when `navigator.serviceWorker` is unavailable (returns resolved nil).

## Cache Naming (no SW-specific version)

The library is version-agnostic. It uses the cache names the app supplies. For
purge-on-deploy, the app embeds its existing build version into the names it passes
(e.g. `(str "shell-" app-version)`). The `activate` keep-set logic then drops prior-version
caches automatically. No version field in `ctx`; no version concept inside the SW lib.

## Testing (TDD, behavior over seams)

Speclj specs per namespace. `service-worker-fake` provides a real in-memory `CacheStorage`
so specs assert *what was cached* and *what response was returned*, not *which function was
invoked*. Coverage:

- Each strategy: hit/miss/network-failure → correct response + correct cache writes.
- Route registry: matcher normalization (fn/regex/string), first-match-wins, default fallthrough.
- Lifecycle: install precaches + skipWaiting; activate purges only non-keep caches + claims;
  fetch routes GET vs passes through non-GET.
- Registration: registers, wires update callbacks, no-ops without `serviceWorker`.

## Security (secure by default)

A service worker intercepts every request under its scope and persists responses on disk.
That makes it a security-sensitive surface; the defaults must be conservative and the unsafe
choices must be explicit opt-ins.

- **Secure context only.** `register!` no-ops gracefully (resolved nil) when not in a secure
  context (`self.isSecureContext` false). HTTPS/localhost is browser-enforced; we fail closed,
  never throw.
- **Same-origin caching by default.** Strategies cache only same-origin requests. Cross-origin
  caching requires an explicit per-route opt-in (`:allow-cross-origin true`). Prevents silently
  persisting third-party content.
- **No opaque-response caching.** Opaque (`type` `"opaque"`/`"opaqueredirect"`) and non-`ok`
  responses are never written to a cache. Avoids cache poisoning and storing partial/error bodies.
- **Respect `no-store`.** Responses with `Cache-Control: no-store` are not cached, even on an
  otherwise-cacheable route.
- **Respect `no-cache` and `private`.** In addition to `no-store`, responses whose
  `Cache-Control` directive contains `no-cache` or `private` are never cached. The check
  tokenizes the header value (case-insensitive, split on whitespace/comma) so any of the
  three directives blocks the write regardless of order or additional directives.
- **Block `Vary: *`, `Vary: Cookie`, `Vary: Authorization`.** Responses that vary on a
  wildcard or on credential-bearing headers indicate per-user content and are never cached.
- **Block `Set-Cookie`.** A response that sets a cookie is treated as session-bearing and
  is never cached.
- **Don't cache credentialed/auth responses by default.** Requests carrying `Authorization`
  headers or `credentials: "include"` are treated as non-cacheable unless the route explicitly
  opts in (`:cache-credentialed true`).
- **Threat-model limitation.** The above blocks cover `Authorization`-header auth and
  `credentials: "include"` auth, plus response-side signals (`Vary`, `Set-Cookie`,
  `Cache-Control: private/no-cache`). They do NOT auto-detect same-origin cookie auth:
  service workers cannot inspect same-origin `Cookie` request headers (the browser strips
  them from `Request` objects inside `ServiceWorkerGlobalScope`). Apps that rely on
  cookie-session authentication without `Authorization` headers must route those endpoints
  to `network-only` (or omit caching) themselves.
- **GET-only writes.** Only `GET` responses are ever cached; non-GET always passes through to
  the network. (Caching a mutation response is meaningless and risky.)
- **Inert fallback.** The default 503 fallback carries an empty body and no headers derived from
  the request — it leaks nothing.
- **No dynamic code.** Routes are data + pure predicates; no `eval`, no string-compiled handlers.
- **Body-clone discipline.** A `Response` body is single-use; strategies clone before caching so
  the returned response is never a consumed stream.
- **Scope is documented, not widened.** The library registers at the URL the app gives it and
  does not broaden scope. Docs warn that SW scope controls all paths beneath it.

These rules live in one place — a `cacheable?` predicate the strategies share — so the policy is
testable in isolation and applied uniformly. Each rule gets a failing test first.

## SOLID Mapping

- **SRP** — one responsibility per namespace/unit: strategies, route registry, lifecycle
  handlers, page registration, and fakes are each separate. A change to purge logic doesn't
  touch strategy code.
- **OCP** — adding a new strategy is a new constructor returning the same handler shape; the
  fetch dispatch never changes. Matcher kinds extend through one normalization step, not by
  editing the registry.
- **LSP** — every strategy satisfies the same contract `(fn [ctx request] -> Promise<Response>)`
  and is freely substitutable anywhere a strategy is expected.
- **ISP** — `ctx` carries only `{:caches :fetch :scope}`; handlers receive exactly what they
  need. No god-config object.
- **DIP** — handlers depend on the injected `ctx` abstractions, never on `js/caches` / `js/fetch`
  globals directly. Production wires real globals; tests wire fakes.

## TDD Discipline

Strict red-green-refactor. No production line without a failing spec first. The `cacheable?`
security predicate, each strategy's hit/miss/failure path, matcher normalization, the
activate keep-set/purge, and registration's secure-context + callback wiring each start as a
red test. Behavior assertions only (what was cached / what response returned), never seam
assertions.

## Open-Source Readiness

- **License/headers** — inherits the repo's existing license; no new third-party code, no
  copied wilson source.
- **Public API documented** — docstrings on every public fn; a usage section in the wire README
  (or `docs/`) showing a minimal SW namespace + page registration.
- **No app-specific leakage** — zero hardcoded paths, asset names, schemas, or endpoints from any
  app. All such values are caller-supplied.
- **No secrets, no telemetry** — the library makes no network calls of its own beyond the
  fetches a strategy is explicitly asked to perform.
- **Self-contained tests** — fakes ship in `src/`, so the public test surface is reusable and the
  examples in docs are runnable.

## Improvements Over Wilson

- No `js-core` passthrough stub-seam namespace — DI instead.
- No custom `with-promise` macro — native interop helper.
- Per-route configurable strategies (the standard five) instead of one network-first-for-all-GETs.
- Real update/active callbacks on registration instead of log-only.
- Cache invalidation via app-version-in-name + keep-set purge, no hardcoded literal names.
- Reusable in-memory cache fakes shipped for app tests.
- App-specific paths and entity-sync removed from the library boundary.

## File Layout

```
src/cljs/c3kit/wire/service_worker.cljs
src/cljs/c3kit/wire/service_worker_register.cljs
src/cljs/c3kit/wire/service_worker_fake.cljs
spec/cljs/c3kit/wire/service_worker_spec.cljs
spec/cljs/c3kit/wire/service_worker_register_spec.cljs
```
