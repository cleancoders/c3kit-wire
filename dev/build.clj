(ns build
  (:require [cleancoders.build.digest :as digest]
            [cleancoders.build.publish-verify :as publish-verify]
            [cleancoders.build.release :as release]
            [cleancoders.build.sbom :as sbom]
            [cemerick.pomegranate.aether :as aether]
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
(def core-sbom-file  (format "target/wire-core-%s-cyclonedx.json" version))
(def react-sbom-file (format "target/wire-%s-cyclonedx.json" version))

(def pom-template
  [[:licenses
    [:license
     [:name "MIT License"]
     [:url "https://github.com/cleancoders/c3kit-wire/blob/master/LICENSE"]]]])

(defn clean [_]
  (println "cleaning")
  (b/delete {:path "target"}))

(defn- core-basis []
  ;; The main :deps map is the React-free core dep set, so the default basis
  ;; (no aliases) is exactly what wire-core's pom should declare.
  (b/create-basis {:project "deps.edn"}))

(defn- react-basis []
  ;; The wire jar is self-contained — it ships all of wire-core's sources plus
  ;; the React wrappers — so its pom needs every runtime dep. The :react alias
  ;; layers reagent + cljsjs/react* on top of the core :deps, producing exactly
  ;; that union.
  (b/create-basis {:project "deps.edn" :aliases [:react]}))

(defn- write-sbom!
  "Writes the CycloneDX document for one artifact. Takes that artifact's own
   basis, not a shared one: wire-core's pom declares the React-free dep set and
   wire's declares the union, so a single SBOM would misdescribe one of them.
   The jar's digest goes in so the document names the exact bytes it describes."
  [lib basis jar-file sbom-file]
  (sbom/write! {:lib        lib
                :version    version
                :basis      basis
                :jar-digest (digest/sha256 jar-file)
                :sbom-file  sbom-file}))

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
            :jar-file  core-jar-file})
    (write-sbom! core-lib basis core-jar-file core-sbom-file)))

(defn jar-react [_]
  (println "building" react-jar-file)
  (let [basis (react-basis)]
    ;; Self-contained: ship every source root. The wire jar duplicates wire-core's
    ;; content on purpose so that pulling wire alone works as a drop-in for 3.0.0,
    ;; with no transitive dep on wire-core.
    (b/copy-dir {:src-dirs   ["src/clj" "src/cljc" "src/cljs" "src/cljs-react"]
                 :target-dir react-class-dir})
    (b/write-pom {:basis     basis
                  :class-dir react-class-dir
                  :lib       react-lib
                  :version   version
                  :pom-data  pom-template})
    (b/jar {:class-dir react-class-dir
            :jar-file  react-jar-file})
    (write-sbom! react-lib basis react-jar-file react-sbom-file)))

(defn- deploy-config
  "The pomegranate deploy map for one artifact.

   :artifact-map carries the SBOM and nothing else. :jar-file and :pom-file
   above already upload the jar and the pom, and aether honors both -- naming
   the jar in the artifact-map as well would upload it twice."
  [lib jar-file class-dir sbom-file]
  {:coordinates       [lib version]
   :jar-file          jar-file
   :pom-file          (str/join "/" [class-dir "META-INF/maven" group-name (name lib) "pom.xml"])
   :repository        {"clojars" {:url      "https://clojars.org/repo"
                                  :username (System/getenv "CLOJARS_USERNAME")
                                  :password (System/getenv "CLOJARS_PASSWORD")}}
   :artifact-map      {[:classifier "cyclonedx" :extension "json"] sbom-file}
   :transfer-listener :stdout})

(defn jar [_]
  (clean nil)
  (jar-core nil)
  (jar-react nil))

(defn install [_]
  (jar nil)
  (println "installing wire-core" version)
  (aether/install (deploy-config core-lib core-jar-file core-class-dir core-sbom-file))
  (println "installing wire" version)
  (aether/install (deploy-config react-lib react-jar-file react-class-dir react-sbom-file)))

(defn- publish! []
  (println "deploying wire-core" version)
  (aether/deploy (deploy-config core-lib core-jar-file core-class-dir core-sbom-file))
  (println "deploying wire" version)
  (aether/deploy (deploy-config react-lib react-jar-file react-class-dir react-sbom-file)))

(defn- pom-file [lib class-dir]
  (str/join "/" [class-dir "META-INF/maven" group-name (name lib) "pom.xml"]))

(defn- artifact-entries
  "The three files one artifact ships, for post-publish verification, the digest
   record, and the tag message. The jar carries a :url so its published bytes get
   re-fetched and compared; the pom and the SBOM are recorded but not re-fetched,
   matching what the library's own single-jar path does.

   Both jars get a url, not just one -- this repo publishes two independent
   artifacts, and verifying only one of them would leave the other's bytes
   unchecked while the release still tagged."
  [lib jar-file class-dir sbom-file]
  [{:name (str (name lib) "-" version ".jar") :path jar-file :digest (digest/sha256 jar-file)
    :url  (publish-verify/artifact-url {:lib lib :version version})}
   {:name (str (name lib) "-" version ".pom") :path (pom-file lib class-dir)
    :digest (digest/sha256 (pom-file lib class-dir))}
   {:name (str (name lib) "-" version "-cyclonedx.json") :path sbom-file
    :digest (digest/sha256 sbom-file)}])

(defn- artifacts []
  (into (artifact-entries core-lib core-jar-file core-class-dir core-sbom-file)
        (artifact-entries react-lib react-jar-file react-class-dir react-sbom-file)))

(defn deploy [_]
  (release/deploy! {:repo        "cleancoders/c3kit-wire"
                    :ci-workflow "build.yml"
                    :version     version
                    :jar!        #(jar nil)
                    :publish!    publish!
                    :artifacts   artifacts}))

(defn emergency-publish [_]
  (release/emergency-deploy! {:version   version
                              :jar!      #(jar nil)
                              :publish!  publish!
                              :artifacts artifacts}))
