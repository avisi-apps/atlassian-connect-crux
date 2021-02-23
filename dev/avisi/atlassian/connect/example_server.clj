(ns avisi.atlassian.connect.example-server
  (:require
    [mount.core :refer [defstate]]
    [ring.adapter.jetty :as jetty]
    [avisi.atlassian.connect.server :refer [defhandler]]
    [avisi.atlassian.connect.example-atlassian-connect :as atlassian-connect])
  (:import
    [org.eclipse.jetty.server Server]))

(defhandler
  app-handler
  {:dev? true
   :routes []
   :atlassian-connect-edn atlassian-connect/edn})

(defstate server
  :start
    (jetty/run-jetty
      app-handler
      {:port 3000
       :join? false
       :async true})
  :stop (.stop ^Server server))
