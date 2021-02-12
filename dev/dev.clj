(ns dev
  (:require
    [clojure.tools.namespace.repl :as tn]
    [avisi.atlassian.connect.example-server :as server]
    [avisi.atlassian.connect.example-atlassian-connect :as atlassian-connect]
    [avisi.atlassian.connect.firestore :as connect-firestore]
    [mount.core :as mount]))

(defn start [] (mount/start #'server/server #'atlassian-connect/edn))

(defn stop [] (mount/stop))

(defn refresh [] (stop) (tn/refresh))

(defn refresh-all [] (stop) (tn/refresh-all))

(defn go "starts all states defined by defstate" [] (start) :ready)

(defn reset "stops all states defined by defstate, reloads modified source files, and restarts the states"
  []
  (stop)
  (tn/refresh :after 'dev/go))

(defn get-install []
  (first
    (connect-firestore/collection-entities
      {::connect-firestore/firestore connect-firestore/service
       ::connect-firestore/collection "hosts"
       ::connect-firestore/firestore nil})))
