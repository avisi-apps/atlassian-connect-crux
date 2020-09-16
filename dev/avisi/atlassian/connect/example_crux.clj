(ns avisi.atlassian.connect.example-crux
  (:require
    [crux.api :as crux]
    [mount.core :refer [defstate]])
  (:import
    [java.io Closeable]))

(def opts
  {:crux.node/topology :crux.standalone/topology
   :crux.node/kv-store "crux.kv.rocksdb/kv"
   :crux.standalone/event-log-dir "target/data/event-log-dir"
   :crux.kv/db-dir "target/data/db-dir"})

(defstate node :start (crux/start-node opts) :stop (.close ^Closeable node))
