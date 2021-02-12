(ns avisi.atlassian.connect.firestore-test
  (:require
    [clojure.test :as t]
    [fixtures.firestore-fixtures :as firestore-fixtures]
    [avisi.atlassian.connect.firestore :as firestore])
  (:import
    (com.google.cloud Timestamp)
    (java.util Date)))

(t/use-fixtures :each firestore-fixtures/with-test-firestore)

(t/deftest should-transform-all-host-keys
  (t/is
    (=
      {:atlassian-connect.host/base-url "https://avisi-support.atlassian.net"
       :atlassian-connect.host/client-key "client-key"}
      (firestore/namespace-all-keys
        {:baseUrl "https://avisi-support.atlassian.net"
         :clientKey "client-key"})))
  (t/is
    (=
      {"atlassian-connect.host/base-url" "https://fatihdemir-new-dev.atlassian.net"
       "atlassian-connect.host/client-key" "client-key"
       "list" ["foo" "bar" "baz"]}
      (firestore/keys-as-string
        {:atlassian-connect.host/base-url "https://fatihdemir-new-dev.atlassian.net"
         :atlassian-connect.host/client-key "client-key"
         :list [:foo :bar :baz]})))
  (t/is (let [date (Date.) timestamp (Timestamp/of date)] (= date (firestore/timestamps-to-java-date timestamp)))))

(t/deftest should-retry-save-host-on-failed-cas-op
  (t/is
    (=
      {"clientKey" "test"
       "eventType" "installed"}
      (firestore/update-host-doc!
        {::firestore/firestore firestore-fixtures/*firestore*
         :prev-host nil
         :new-host
           {"clientKey" "test"
            "eventType" "installed"}})))
  (t/is
    (=
      {"clientKey" "test"
       "eventType" "enabled"}
      (firestore/update-host-doc!
        {::firestore/firestore firestore-fixtures/*firestore*
         :prev-host
           {"clientKey" "test"
            "eventType" "uninstalled"}
         :new-host
           {"clientKey" "test"
            "eventType" "enabled"}})))
  (t/is
    (=
      {:atlassian-connect.host/client-key "test"
       :atlassian-connect.host/event-type "enabled"}
      (firestore/get-host
        {::firestore/firestore firestore-fixtures/*firestore*
         ::firestore/host-client-key "test"}))))

(t/deftest crud-firestore
  (let [doc (firestore/keys-as-string
              {:foo "bar"
               :list [:foo :bar :baz]})
        doc-path ["some" "path"]]
    (firestore/store-document!
      {::firestore/firestore firestore-fixtures/*firestore*
       ::firestore/document-path doc-path
       ::firestore/document doc})
    (t/is
      (=
        doc
        (firestore/fetch-document
          {::firestore/firestore firestore-fixtures/*firestore*
           ::firestore/document-path doc-path})))
    (firestore/store-document!
      {::firestore/firestore firestore-fixtures/*firestore*
       ::firestore/document-path doc-path
       ::firestore/document {"another" "document"}})
    (t/is
      (=
        {"another" "document"}
        (firestore/fetch-document
          {::firestore/firestore firestore-fixtures/*firestore*
           ::firestore/document-path doc-path})))
    (firestore/delete-document
      (firestore/find-document
        {::firestore/firestore firestore-fixtures/*firestore*
         ::firestore/collection "some"
         ::firestore/field "another"
         ::firestore/value "document"}))
    (t/is
      (nil?
        (firestore/fetch-document
          {::firestore/firestore firestore-fixtures/*firestore*
           ::firestore/document-path doc-path})))))

(t/deftest finding-documents
  (let [doc (firestore/keys-as-string
              {:foo "bar"
               :list [:foo :bar :baz]})
        sub-doc (firestore/keys-as-string {:sub "doc"})
        sub-doc-2 (firestore/keys-as-string {:sub "doc-2"})
        doc-2 (firestore/keys-as-string
                {:foo "something"
                 :list [:foo :bar :baz]})
        collection "hosts"]
    (firestore/store-document!
      {::firestore/firestore firestore-fixtures/*firestore*
       ::firestore/document-path [collection "doc-1"]
       ::firestore/document doc})
    (firestore/store-document!
      {::firestore/firestore firestore-fixtures/*firestore*
       ::firestore/document-path [collection "doc-1" "sub-collection" "sub-doc"]
       ::firestore/document sub-doc})
    (firestore/store-document!
      {::firestore/firestore firestore-fixtures/*firestore*
       ::firestore/document-path [collection "doc-1" "sub-collection" "sub-doc-2"]
       ::firestore/document sub-doc-2})
    (firestore/store-document!
      {::firestore/firestore firestore-fixtures/*firestore*
       ::firestore/document-path [collection "doc-2"]
       ::firestore/document doc-2})
    (t/is
      (=
        doc
        (firestore/find-entity
          {::firestore/firestore firestore-fixtures/*firestore*
           ::firestore/collection collection
           ::firestore/field "foo"
           ::firestore/value "bar"})))
    (t/is
      (=
        doc-2
        (firestore/find-entity
          {::firestore/firestore firestore-fixtures/*firestore*
           ::firestore/collection collection
           ::firestore/field "foo"
           ::firestore/value "something"})))
    (t/is
      (=
        [sub-doc sub-doc-2]
        (firestore/get-host-sub-collection-documents
          {::firestore/firestore firestore-fixtures/*firestore*
           ::firestore/host-client-key "doc-1"
           ::firestore/collection-path ["sub-collection"]})))
    (t/is
      (=
        doc
        (firestore/get-document-parent-entity
          {::firestore/firestore firestore-fixtures/*firestore*
           ::firestore/collection "sub-collection"
           ::firestore/field "sub"
           ::firestore/value "doc"})))))
