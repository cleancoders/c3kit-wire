# Wire

![Wire](https://github.com/cleancoders/c3kit/blob/master/img/wire_200.png?raw=true)

A library component of [c3kit - Clean Coders Clojure Kit](https://github.com/cleancoders/c3kit).

_"If you look very closely at just one wire in the cage, you cannot see the other wires."_ - Marilyn Frye

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

# Development

    # Run the JVM tests
    clj -M:test:spec
    clj -M:test:spec -a         # auto runner

    # Compile and Run JS tests
    clj -M:test:cljs once
    clj -M:test:cljs            # auto runner

# Deployment

In order to deploy to c3kit you must be a member of the Clojars group `com.cleancoders.c3kit`.

1. Go to https://clojars.org/tokens and configure a token with the appropriate scope
2. Add the following to ~/.m2/settings.xml

```xml
<servers>
    <server>
        <id>clojars</id>
        <username>[clojars username]</username>
        <password>[deploy token]</password>
    </server>
</servers>
```

3. If dependencies were changed, run `clj -Spom` to regenerate the `pom.xml` file in the root dir of the project.
4. Update the `version` in `pom.xml` and ensure that the `groupId` and `artifactId` are set for the project (e.g. `com.cleancoders.c3kit` and `wire`, respectively)
5. Build the jar using `clj -T:build jar`
6. Deploy to maven `mvn deploy`
