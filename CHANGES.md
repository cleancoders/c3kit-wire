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
