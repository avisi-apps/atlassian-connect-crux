(ns avisi.atlassian.connect.crux-test
  (:require
    [clojure.test :as t]
    [crux.api :as crux]
    [avisi.atlassian.connect.crux :as connect-crux]
    [crux.io :as cio]
    [cambium.core :as log]))

(def ^:dynamic *crux-node* nil)

(def test-opts
  {:crux.node/topology '[crux.standalone/topology crux.kv.rocksdb/kv-store]
   :crux.kv/sync? false})

(defn with-crux [f]
  (let [event-log-dir (str (cio/create-tmpdir "event-log-dir"))]
    (try
      (with-open [crux-node (crux/start-node (assoc test-opts :crux.kv/db-dir event-log-dir))]
        ;; This seems to make sure that we don't start our function too fast
        (crux/sync crux-node)
        (binding [*crux-node* crux-node] (f)))
      (finally (cio/delete-dir event-log-dir)))))

(t/use-fixtures :each with-crux)

(t/deftest should-transform-all-host-keys
  (t/is
    (=
      {:atlassian-connect.host/base-url "https://avisi-support.atlassian.net"
       :atlassian-connect.host/client-key "client-key"}
      (connect-crux/namespace-all-keys
        {:baseUrl "https://avisi-support.atlassian.net"
         :clientKey "client-key"}))
    "Should transform all camelCase to namespaced kebab-keys"))

(t/deftest should-retry-save-host-on-failed-cas-op
  (t/is
    (nil?
      (connect-crux/update-host!
        {:prev-host nil
         :new-host
           {:atlassian-connect.host/client-key "test"
            :atlassian-connect.host/event-type "installed"
            :crux.db/id (connect-crux/client-key->crux-id "test")}
         :crux-node *crux-node*})))
  (t/is
    (nil?
      (connect-crux/update-host!
        {:prev-host
           {:atlassian-connect.host/client-key "test"
            ;; This is the wrong event-type because we are currently "installed"
            ;; This will trigger a retry
            :atlassian-connect.host/event-type "uninstalled"
            :crux.db/id (connect-crux/client-key->crux-id "test")}
         :new-host
           {:atlassian-connect.host/client-key "test"
            :atlassian-connect.host/event-type "enabled"
            :crux.db/id (connect-crux/client-key->crux-id "test")}
         :crux-node *crux-node*})))
  (t/is
    (=
      {:atlassian-connect.host/client-key "test"
       :atlassian-connect.host/event-type "enabled"
       :crux.db/id (connect-crux/client-key->crux-id "test")}
      (connect-crux/get-installation (crux/db *crux-node*) "test"))))
