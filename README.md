# Wire

![Wire](https://github.com/cleancoders/c3kit/blob/master/img/wire_200.png?raw=true)

A library component of [c3kit - Clean Coders Clojure Kit](https://github.com/cleancoders/c3kit).

_"If you look very closely at just one wire in the cage, you cannot see the other wires."_ - Marilyn Frye

[![Wire Build](https://github.com/cleancoders/c3kit-wire/actions/workflows/test.yml/badge.svg)](https://github.com/cleancoders/c3kit-wire/actions/workflows/test.yml)
[![Clojars Project](https://img.shields.io/clojars/v/com.cleancoders.c3kit/wire.svg)](https://clojars.org/com.cleancoders.c3kit/wire)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Wire is a toolset for building rich-client web applications, clojure on the server side and clojurescript on the client side.

 * __ajax.clj(s)__ : AJAX comm between client and server
 * __api(c).clj(c)(s)__ : common api for data used by ajax and websocket
 * __assets.clj__ : update asset filenames when the content changes
 * __flash(c).clj(c)(s)__ : flash messages
 * __refresh.clj__ : dynamic code-reloading in development
 * __spec-helper(c).clj(c)(s)__ : help test client code
 * __verbose.clj__ : print request/response info in development
 * __websocket(c).clj(c)(s)__ : support for websocket comm between client and server
 * __dragndrop.cljs__ : simple client side drag and drop
 * __js.cljs__ : common js fns and features
 * __util.cljs__ : misc utilities

# Artifacts

Starting with 4.0.0, wire publishes two independent jars to Clojars. Pick one — pulling both onto the same classpath produces duplicate `c3kit.wire.*` entries.

 * __`com.cleancoders.c3kit/wire`__ — React-flavored. Self-contained: ships everything `wire-core` ships plus the Reagent wrappers and `reagent` / `cljsjs/react*` deps. The drop-in for projects that were on 3.0.0.
 * __`com.cleancoders.c3kit/wire-core`__ — React-free. Same `c3kit.wire.*` namespaces minus the Reagent wrappers, plus `c3kit.wire.core.{ajax,rest,websocket}`. No React deps. Pull this if your project doesn't use React.

The two artifacts share source for everything below the React layer; the React-flavored namespaces (`c3kit.wire.ajax/rest/websocket/flash/spec-helper`) are thin Reagent wrappers over the corresponding `c3kit.wire.core.*` namespaces.

# Repository Layout

The repo mirrors the artifact split: React-bearing code lives under `*-react/` directories, React-free code lives under their unsuffixed siblings.

```
src/clj           JVM code (used by both jars)
src/cljc          cross-platform shared code (used by both jars)
src/cljs          React-free CLJS — c3kit.wire.core.* and friends (used by both jars)
src/cljs-react    React-flavored CLJS wrappers (wire jar only)

spec/clj          JVM specs
spec/cljc         shared specs
spec/cljs         React-free CLJS specs
spec/cljs-react   React-flavored CLJS specs

dev/              JVM dev resources (datomic config, build script, demo assets)
dev-core/         scaffold cljs config for the React-free test run
dev-react/        scaffold cljs config for the React-bearing test run
```

`deps.edn` is organized so that the default `:deps` is the React-free core dep set, and a `:react` alias adds `reagent` + `cljsjs/react*` plus the `src/cljs-react`, `spec/cljs-react`, and `dev-react` paths on top. There is a single source of truth for the core dep list; the React layer is purely additive.

# Development

JVM tests don't need React (Reagent is CLJS-only), so they're the same regardless of which artifact you're working on:

    clj -M:test:spec
    clj -M:test:spec -a              # auto runner

CLJS tests run the React-free suite by default. Add `:react` to also run the React-bearing wrappers and their specs:

    # wire-core (React-free) — what publishes as the wire-core jar
    clj -M:test:cljs once
    clj -M:test:cljs                 # auto runner

    # wire (with React) — what publishes as the wire jar
    clj -M:test:react:cljs once
    clj -M:test:react:cljs           # auto runner

The same `:react` toggle applies to any CLJS workflow — chain it in when you need the Reagent layer, leave it out for the React-free core.

`clj -M:test:cljs once` doubles as the classpath-isolation guarantee: if a `c3kit.wire.core.*` namespace ever accidentally `(:require [reagent.core ...])`, this run fails because reagent isn't on the classpath without `:react`.

    # Install Redis (needed by some integration specs)
    brew install redis

# Local Development with `:local/root` or `:git/url`

Maven consumers (the `:mvn/version` case) are unaffected by the deps.edn layout — they consume the published poms, which list the full set of runtime deps for whichever artifact they pulled.

If you depend on `c3kit-wire` via `:local/root` or `:git/url`, your project reads this `deps.edn` directly. The default `:deps` is React-free, so add the `:react` alias to your alias chain (or pin `reagent` / `cljsjs/react*` yourself) when you need the Reagent layer.

# Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow, TDD
expectations, the dual-jar story, and pull-request requirements. This project
follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).
Security issues should be reported privately — see [SECURITY.md](SECURITY.md).

# Deployment

You must be a member of the Clojars group `com.cleancoders.c3kit`.

1. Generate a deploy token at https://clojars.org/tokens with the appropriate scope.
2. Export credentials:

```
CLOJARS_USERNAME=<your username>
CLOJARS_PASSWORD=<your deploy key>
```

3. Update `VERSION`.
4. `clj -T:build deploy`

`clj -T:build deploy` cleans, builds both jars, and pushes them to Clojars.
`clj -T:build jar` builds without deploying. Both jars share the same VERSION.

## Service Worker (offline caching)

Two namespaces: `c3kit.wire.service-worker` runs inside the service worker
(`ServiceWorkerGlobalScope`); `c3kit.wire.service-worker-register` runs on the page.
Caching is secure by default: same-origin, `ok`, non-opaque, GET-only, with hard
blocks on `Cache-Control: no-store/no-cache/private` and `Vary: */Cookie/Authorization`.
Credentials are detected via `Authorization` header or `credentials: "include"` and are
blocked by default.

**Security note — threat-model limitations (read before caching anything private):**
The hard blocks reliably cover `Authorization`-header and `credentials: "include"` auth
and, on same-origin responses, `Cache-Control: private/no-cache/no-store` and `Vary`.
Two guards **cannot fire in a live browser** — do not rely on them:

- **`Set-Cookie`** is a forbidden response-header name, so `headers.get("Set-Cookie")`
  returns `nil` inside the service worker. A `Set-Cookie` response is *not* detected and
  will be cached if nothing else blocks it.
- **`Vary`** is not a CORS-safelisted response header, so on a **cross-origin** `cors`
  response `headers.get("Vary")` is `nil` and the `Vary` guard is silently skipped. It
  works only same-origin.
- **Same-origin cookie-session auth** is invisible: cookie headers are stripped from
  `Request` objects in `ServiceWorkerGlobalScope`.

For any private, per-user, session-cookie, or `Set-Cookie` endpoint, route it explicitly
to `network-only` (or omit caching) — the secure-by-default gate alone is not sufficient.

Strategies available inside the service worker: `cache-first`, `network-first`,
`stale-while-revalidate`, `cache-only`, `network-only`. Each is a factory — call
it with an opts map, register the result as a route handler.

Your service worker entry (compiled to `/service-worker.js`):

```clojure
(ns my-app.service-worker
  (:require [c3kit.wire.service-worker :as sw]))

(defn -main []
  (sw/precache! ["/" "/css/app.css" "/img/logo.png"] (str "shell-" my-app/version))
  (sw/register-route! #"/img/"  (sw/cache-first {:cache "images"}))
  (sw/register-route! #"/api/"  (sw/network-first {:cache "api"}))
  (sw/register-route! "/"       (sw/stale-while-revalidate {:cache "pages"}))
  (sw/register-route! #"/static/" (sw/cache-only {:cache "static"}))
  (sw/set-default!              (sw/network-only {}))
  (sw/start!))
```

On the page:

```clojure
(ns my-app.main
  (:require [c3kit.wire.service-worker-register :as swr]))

(swr/register! {:url "/service-worker.js"
                :on-update (fn [_] (js/console.log "update available"))})

;; To remove the service worker (e.g. when rolling back):
(swr/unregister!)
```

Embed your app version in cache names (e.g. `(str "shell-" version)`) to get
automatic purge of prior-deploy caches on `activate`.

**stale-while-revalidate caveat:** background revalidation is fire-and-forget and is
not wrapped in `event.waitUntil`, so a browser may terminate the service worker before
the refresh completes. The next request still serves the prior cached value and
re-triggers revalidation. For must-refresh resources prefer `network-first`.

**precache! atomicity:** URLs passed to `precache!` are handed to `cache.addAll`, which
is atomic — if any single URL returns a 4xx/5xx or network-fails, the entire install
fails and the service worker never activates. Every URL must be an absolute or
same-origin path that resolves successfully at install time.

**Security footguns — `:allow-cross-origin` and `:cache-credentialed`:** these opts
are explicit opt-outs of the secure-by-default caching gate. Use them only with a
tight matcher (exact host + path, narrow regexp) that covers exactly the resources
you intend to cache. A broad matcher (e.g. `(fn [_] true)`) with either opt enabled
can cause the cache to store attacker-influenced cross-origin responses or per-user
credentialed responses, potentially leaking private data across sessions.

# License

[MIT](LICENSE) © Clean Coders.
