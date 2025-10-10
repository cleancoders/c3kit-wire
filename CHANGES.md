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
