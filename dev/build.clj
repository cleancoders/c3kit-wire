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
  ;; The main :deps map is the React-free core dep set, so the default basis
  ;; (no aliases) is exactly what wire-core's pom should declare.
  (b/create-basis {:project "deps.edn"}))

(defn- react-basis []
  ;; The wire jar is self-contained — it ships all of wire-core's sources plus
  ;; the React wrappers — so its pom needs every runtime dep. The :react alias
  ;; layers reagent + cljsjs/react* on top of the core :deps, producing exactly
  ;; that union.
  (b/create-basis {:project "deps.edn" :aliases [:react]}))

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
  (jar nil)
  (println "installing wire-core" version)
  (aether/install (deploy-config core-lib core-jar-file core-class-dir))
  (println "installing wire" version)
  (aether/install (deploy-config react-lib react-jar-file react-class-dir)))

(defn deploy [_]
  (tag nil)
  (jar nil)
  (println "deploying wire-core" version)
  (aether/deploy (deploy-config core-lib core-jar-file core-class-dir))
  (println "deploying wire" version)
  (aether/deploy (deploy-config react-lib react-jar-file react-class-dir)))
