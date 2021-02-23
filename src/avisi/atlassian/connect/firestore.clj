(ns avisi.atlassian.connect.firestore
  (:require
    [mount.core :as m :refer [defstate]]
    [com.fulcrologic.guardrails.core :refer [>defn >def => ?]]
    [clojure.spec.alpha :as s]
    [camel-snake-kebab.core :as csk]
    [avisi.atlassian.connect.jwt :as jwt]
    [cambium.core :as log]
    [clojure.string :as string]
    [clojure.string :as str]
    [clojure.set :as set])
  (:import
    (com.google.cloud.firestore
      FirestoreOptions
      FirestoreOptions$Builder
      Firestore
      DocumentReference
      CollectionReference
      DocumentSnapshot
      Query
      FieldPath
      QueryDocumentSnapshot
      QuerySnapshot)
    (com.google.api.core ApiFuture)
    (java.util HashMap ArrayList)
    (java.time Duration)
    (java.util Date UUID)
    (com.google.cloud Timestamp)))

(set! *warn-on-reflection* true)

(>def ::collection-path vector?)
(>def ::document-path vector?)
(>def ::document map?)
(>def ::host-client-key string?)
(>def ::collection string?)
(>def ::field string?)
(>def ::value string?)
(>def ::firestore (fn [x] (instance? Firestore x)))

(defn transform-map-keys [m f]
  (reduce (fn [acc [k v]] (assoc acc (f k) (cond->> v (or (vector? v) (instance? ArrayList v)) (mapv f)))) {} m))

(defn keys-as-string [m]
  (transform-map-keys
    m
    #(->
        %
        str
        (subs 1))))

(defn timestamps-to-java-date [^Timestamp t] (.toDate t))

(defstate options
  :start
  (->
    (FirestoreOptions/newBuilder)
    ^FirestoreOptions$Builder (.setProjectId (:firestore-project-id (m/args)))
    (.build)))

(defstate service :start ^Firestore (.getService ^FirestoreOptions options) :stop (.close ^Firestore service))

(>defn path->string [document-path] [::document-path => string?] (str/join "/" document-path))

(>defn store-document!
  [{::keys [firestore document-path document]}]
  [(s/keys :req [::firestore ::document-path ::document]) => any?]
  (->
    ^Firestore firestore
    ^DocumentReference (.document (path->string document-path))
    ^ApiFuture (.set document)
    (.get)))

(defn doc-ref->doc [^DocumentReference doc-ref]
  (some->>
    (->
      doc-ref
      ^ApiFuture (.get)
      ^DocumentSnapshot (.get)
      (.getData))
    (into {})))

(defn doc-ref->entity [^DocumentReference doc-ref]
  (some->>
    (doc-ref->doc doc-ref)))

(>defn fetch-doc-ref
  [{::keys [firestore document-path]}]
  [(s/keys :req [::firestore ::document-path]) => any?]
  (->
    ^Firestore firestore
    ^DocumentReference (.document (path->string document-path))))

(>defn fetch-document
  [env]
  [(s/keys :req [::firestore ::document-path]) => any?]
  (->
    (fetch-doc-ref env)
    doc-ref->doc))

(>defn store-host!
  [{::keys [firestore document]}]
  [(s/keys :req [::firestore ::document]) => any?]
  (store-document!
    {::document-path ["hosts" (get document "clientKey")]
     ::document document
     ::firestore firestore})
  document)

(>defn store-host-sub-document!
  [{::keys [firestore host-client-key document-path document]}]
  [(s/keys :req [::firestore ::host-client-key ::document ::document-path]) => any?]
  (store-document!
    {::document-path (into ["hosts" host-client-key] document-path)
     ::document document
     ::firestore firestore})
  document)

(>defn get-host-sub-document
  [{::keys [firestore host-client-key document-path]}]
  [(s/keys :req [::firestore ::host-client-key ::document-path]) => (? map?)]
  (fetch-document
    {::document-path ["hosts" host-client-key document-path]
     ::firestore firestore}))

(>defn get-host-sub-collection-documents
  [{::keys [firestore host-client-key collection-path]}]
  [(s/keys :req [::host-client-key ::collection-path]) => vector?]
  (let [doc-refs (->
                   ^Firestore firestore
                   ^CollectionReference (.collection (path->string (into ["hosts" host-client-key] collection-path)))
                   ^"[Lcom.google.cloud.firestore.DocumentReference;" (.listDocuments))]
    (mapv doc-ref->entity doc-refs)))

(defn find-document [{::keys [firestore collection field value]}]
  (->
    ^Firestore firestore
    ^Query (.collectionGroup collection)
    ^Query (.whereEqualTo ^FieldPath (FieldPath/of (into-array [field])) value)
    ^ApiFuture (.get)
    ^QuerySnapshot (.get)
    ^"[Lcom.google.cloud.firestore.QueryDocumentSnapshot;" (.getDocuments)
    ^QueryDocumentSnapshot first
    ^DocumentReference (.getReference)))

(defn find-entity [env]
  (->
    (find-document env)
    (doc-ref->entity)))

(defn get-document-parent-entity [env]
  (->
    ^DocumentReference (find-document env)
    ^CollectionReference (.getParent)
    ^DocumentReference (.getParent)
    doc-ref->entity))

(defn delete-document [^DocumentReference doc-ref] (.get (.delete doc-ref)))

(>defn get-host-doc
  [{::keys [firestore host-client-key]}]
  [(s/keys :req [::firestore ::host-client-key]) => (? map?)]
  (fetch-document
    {::document-path ["hosts" host-client-key]
     ::firestore firestore}))

(defn namespace-all-keys
  "Change all keys from camelCase to kebab-case namespace keywords
  Example: clientKey will become :atlassian-connect.host/client-key"
  [m]
  (reduce (fn [m [k v]] (assoc m (keyword "atlassian-connect.host" (csk/->kebab-case (name k))) v)) {} m))

(defn camel-case-all-keys [m] (reduce (fn [m [k v]] (assoc m (csk/->camelCase (name k)) v)) {} m))

(>defn get-host
  [env]
  [(s/keys :req [::firestore ::host-client-key]) => (? map?)]
  (->
    (get-host-doc env)
    namespace-all-keys))

(defn collection-entities [{::keys [collection firestore]}]
  (mapv doc-ref->entity
    (->
      ^Firestore firestore
      ^CollectionReference (.collection collection)
      ^"[Lcom.google.cloud.firestore.DocumentReference;" (.listDocuments))))

(defn update-host-doc!
  [{:keys [prev-host new-host]
    ::keys [firestore]}]
  (store-host!
    {::document (merge prev-host new-host)
     ::firestore firestore}))

(defn handle-lifecycle-payload! [firestore request]
  (let [lifecycle-payload (keys-as-string (:body-params request))
        _ (log/info (select-keys lifecycle-payload ["baseUrl" "clientKey" "eventType"]) "Handling host lifecycle call")
        client-key (get lifecycle-payload "clientKey")
        prev-host (get-host-doc
                    {::firestore firestore
                     ::host-client-key client-key})]
    ;; Check JWT token on subsequent calls, this prevents tinkering with shared-secrets of hosts.
    (when prev-host
      (jwt/validate-jwt-token
        request
        {:validate-qsh? true
         :shared-secret (get prev-host "sharedSecret")}))
    (update-host-doc!
      {:prev-host prev-host
       :new-host lifecycle-payload
       ::firestore firestore})))

(defn get-installation [firestore client-key]
  (get-host
    {::firestore firestore
     ::host-client-key client-key}))
