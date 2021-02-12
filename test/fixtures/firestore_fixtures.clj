(ns fixtures.firestore-fixtures
  (:require
    [avisi.atlassian.connect.firestore :as firestore]
    [mount.core :as mount :refer [defstate]]
    [clojure.test :refer :all])
  (:import
    (com.google.cloud.firestore FirestoreOptions FirestoreOptions$Builder Firestore)))

(def ^:dynamic *firestore* nil)

(defn with-test-firestore [f]
  (binding [*firestore* ^Firestore
                        (.getService
                          ^FirestoreOptions
                          (->
                            (FirestoreOptions/newBuilder)
                            ^FirestoreOptions$Builder (.setProjectId "dev")
                            (.build)))]
    (f)))
