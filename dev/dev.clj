(ns dev
  (:require [avisi.atlassian.connect.example-crux :as crux-node]
            [clojure.tools.namespace.repl :as tn]
            [avisi.atlassian.connect.example-server :as server]
            [avisi.atlassian.connect.example-atlassian-connect :as atlassian-connect]
            [mount.core :as mount]
            [crux.api :as crux]))

(defn start []
  (mount/start #'crux-node/node
               #'server/server
               #'atlassian-connect/edn))

(defn stop []
  (mount/stop))

(defn refresh []
  (stop)
  (tn/refresh))

(defn refresh-all []
  (stop)
  (tn/refresh-all))

(defn go
  "starts all states defined by defstate"
  []
  (start)
  :ready)

(defn reset
  "stops all states defined by defstate, reloads modified source files, and restarts the states"
  []
  (stop)
  (tn/refresh :after 'dev/go))

(defn get-install []
  (when-let [eid (ffirst
              (crux/q
               (crux/db crux-node/node)
               {:find '[?e]
                :where '[[?e :atlassian-connect.host/client-key _]]}))]
    (crux/entity
     (crux/db crux-node/node)
     eid)))

(defn get-install-history []
  (let [eid (:crux.db/id (get-install))
        db (crux/db crux-node/node)]
    (crux/history-descending
     db
     (crux/new-snapshot db)
     eid)))

(comment
  (go)

  (get-install-history)

  )
