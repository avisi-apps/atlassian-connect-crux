(ns avisi.atlassian.connect.shadow-modules
  (:require [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set])
  (:import [java.io PushbackReader]))

(defn- get-module-js-inner [manifest wanted-module-id]
  (->
   (reduce
    (fn [m {:keys [module-id output-name depends-on]}]
      (if (or (= module-id wanted-module-id)
              (contains? (:depends-on m) module-id))
        (-> m
            (update :js-files conj output-name)
            (update :depends-on set/union depends-on))
        m))
    {:depends-on #{}
     :js-files '()}
    (reverse manifest))
   :js-files))

(defn load-manifest
  "Load manifest source should be the classpath path to the manifest

  I would suggest loading the manifest in a mount.core/defstate so
  you can re-eval the manifest config during dev"
  [source]
  (with-open [r (io/reader (io/resource source))]
    (edn/read (PushbackReader. r))))

(def
  ^{:doc "This function is memoized by default.
          Use as follows:
          ```
          (-> (load-manifest \"resources/public/js/generated/manifest.edn\")
              (get-module-js :app))
          ```"}
  get-module-js
  (memoize get-module-js-inner))
