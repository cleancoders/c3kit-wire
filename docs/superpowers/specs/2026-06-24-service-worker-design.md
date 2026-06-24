# Service Worker Library — Design

Date: 2026-06-24
Status: Approved (design)

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
| `c3kit.wire.service-worker` | `ServiceWorkerGlobalScope` | strategies, route registry, lifecycle handlers, precache |
| `c3kit.wire.service-worker-register` | window / page | register the SW, drive the update lifecycle |
| `c3kit.wire.service-worker-fake` | test (shipped in `src/cljs`) | in-memory `FakeCacheStorage`/`FakeCache`/fake-fetch for behavior tests |

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
