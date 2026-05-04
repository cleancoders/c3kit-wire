# Contributing to Wire

Thanks for your interest! Wire is part of the [c3kit](https://github.com/cleancoders/c3kit) family of libraries.

## Getting Started

1. Fork and clone the repo.
2. Make sure you have a JDK 21+ and the [Clojure CLI](https://clojure.org/guides/install_clojure) installed.
3. Install Redis (needed by some integration specs):

```sh
brew install redis
```

4. Run the test suite to confirm a green baseline:

```sh
clj -M:test:spec                 # JVM specs (one-shot)
clj -M:test:spec -a              # JVM specs (auto-runner)
clj -M:test:cljs once            # CLJS specs, React-free (wire-core)
clj -M:test:react:cljs once      # CLJS specs, React-bearing (wire)
```

The `:react` alias is the meaningful axis: leave it out to test the React-free `wire-core` artifact, chain it in to test the React-bearing `wire` artifact. `clj -M:test:cljs once` doubles as the classpath-isolation guarantee — if a `c3kit.wire.core.*` namespace ever accidentally requires `reagent.core`, this run fails because reagent isn't on the classpath without `:react`.

## Workflow

**All pull requests must be linked to an open issue.** PRs without a linked issue will be closed without review. Open (or find) an issue first, get a thumbs-up from a maintainer that the change is wanted, then start work. This protects everyone's time — yours and ours.

- Open or find an issue describing the bug or proposed change. Wait for maintainer acknowledgement before starting work on anything non-trivial.
- Create a feature branch off `master`.
- **Use TDD.** Write a failing spec first, then the minimum code to make it pass, then refactor.
- Keep commits small and focused. Write descriptive commit messages.
- Update `CHANGES.md` with a one-line entry under a new or current version section.
- If you change the public API, update docstrings and `README.md` as needed.

## The Dual-Jar Story

Starting with 4.0.0, wire publishes two jars from one repo:

- `com.cleancoders.c3kit/wire` — React-flavored, includes Reagent wrappers and `cljsjs/react*` deps.
- `com.cleancoders.c3kit/wire-core` — React-free, same `c3kit.wire.*` namespaces minus the Reagent wrappers, plus `c3kit.wire.core.{ajax,rest,websocket}`.

React-bearing code lives under `*-react/` directories (`src/cljs-react`, `spec/cljs-react`); the unsuffixed siblings are React-free. Anything you add must respect that split — don't introduce `reagent` requires under `src/cljs` or `src/cljc`.

Both jars publish from a single `VERSION` file, so changes land in both at once.

## Code Style

- Idiomatic Clojure: prefer `->` / `->>` threading, keep functions small and focused.
- `!`-suffix for fns that throw; `->type` / `<-type` symmetry for converters.
- Don't column-align values in maps; use single spaces.
- Keep `cond` predicates and their results on the same line (unless > 120 chars).

## Submitting a PR

1. Confirm your PR is linked to an open issue (use `Closes #N` in the description).
2. Ensure all three test runs pass (`clj -M:test:spec`, `clj -M:test:cljs once`, `clj -M:test:react:cljs once`).
3. Open a PR against `master`.
4. Describe what changed and why.

## Deployment

To deploy you must be a member of the Clojars group `com.cleancoders.c3kit`. See the README's "Deployment" section for the full flow. `clj -T:build deploy` cleans, builds both jars, and pushes them to Clojars from one `VERSION`.

## Reporting Bugs / Requesting Features

Open an issue. Include:
- Which artifact you're on (`wire` vs `wire-core`)
- Wire version, Clojure / ClojureScript version, browser (if CLJS)
- A minimal reproduction
- Expected vs actual behavior
