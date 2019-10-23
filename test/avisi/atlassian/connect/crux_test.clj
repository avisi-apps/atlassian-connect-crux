(ns avisi.atlassian.connect.crux-test
  (:require [clojure.test :as t]
            [crux.api :as crux]
            [avisi.atlassian.connect.crux :as connect-crux]
            [crux.io :as cio])
  (:import [java.io Closeable]))

(def ^:dynamic *crux-node* nil)

(def test-opts {:crux.node/topology :crux.standalone/topology
                :crux.node/kv-store "crux.kv.memdb/kv"})

(defn with-crux [f]
  (let [event-log-dir (str (cio/create-tmpdir "event-log-dir"))
        crux-node (crux/start-node (assoc
                                    test-opts
                                    :crux.standalone/event-log-dir event-log-dir))]
    (binding [*crux-node* crux-node]
      (try
        (f)
        (finally
          (cio/delete-dir event-log-dir)
          (.close ^Closeable *crux-node*))))))

(t/use-fixtures :each with-crux)

(t/deftest should-transform-all-host-keys
  (t/is
   (= {:atlassian-connect.host/base-url "https://avisi-support.atlassian.net"
       :atlassian-connect.host/client-key "client-key"}
      (connect-crux/namespace-all-keys {:baseUrl "https://avisi-support.atlassian.net"
                                        :clientKey "client-key"}))
   "Should transform all camelCase to namespaced kebab-keys"))

(t/deftest ^:kaocha/pending should-save-host
  (t/is (= {}
           (crux/status *crux-node*))))
