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
  (b/create-basis {:project "deps.edn" :aliases [:core-only]}))

(defn- react-basis []
  ;; Inject wire-core at the current VERSION via :extra so the alias itself
  ;; doesn't have to keep a hard-coded version string in sync with VERSION.
  (b/create-basis {:project "deps.edn"
                   :aliases [:react-only]
                   :extra   {:deps {core-lib {:mvn/version version}}}}))

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
    (b/copy-dir {:src-dirs   ["src/cljs-react"]
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
  ;; Install wire-core to local Maven so jar-react's basis can resolve it.
  ;; This is a side effect of building, intentional: the wire jar's pom must
  ;; declare wire-core as a dep at the same version, and tools.deps insists on
  ;; resolving every declared dep. Local install is the simplest way to make
  ;; that resolution succeed without round-tripping through Clojars.
  (println "installing wire-core" version "to local Maven (build prerequisite)")
  (aether/install (deploy-config core-lib core-jar-file core-class-dir))
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
  ;; (jar nil) already installs wire-core to local Maven as a build prerequisite,
  ;; so we only install the wire jar here.
  (jar nil)
  (println "installing wire" version)
  (aether/install (deploy-config react-lib react-jar-file react-class-dir)))

(defn deploy [_]
  (tag nil)
  (jar nil)
  (println "deploying wire-core" version)
  (aether/deploy (deploy-config core-lib core-jar-file core-class-dir))
  (println "deploying wire" version)
  (aether/deploy (deploy-config react-lib react-jar-file react-class-dir)))
