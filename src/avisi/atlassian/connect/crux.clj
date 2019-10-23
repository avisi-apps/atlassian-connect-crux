(ns avisi.atlassian.connect.crux
  (:require [crux.api :as crux]
            [camel-snake-kebab.core :as csk]
            [cambium.core :as log])
  (:import [java.time Duration]))

(defn client-key->crux-id [client-key]
  (keyword "atlassian-connect.host" client-key))

(defn namespace-all-keys
  "Change all keys from camelCase to kebab-case namespace keywords
  Example: clientKey will become :atlassian-connect.host/client-key"
  [m]
  (reduce
   (fn [m [k v]]
     (assoc m (keyword "atlassian-connect.host" (csk/->kebab-case (name k))) v))
   {}
   m))

(defn handle-lifecycle-payload! [crux-node lifecycle-payload]
  (let [lifecycle-payload (namespace-all-keys lifecycle-payload)
        _ (log/info (select-keys lifecycle-payload [:atlassian-connect.host/base-url
                                                    :atlassian-connect.host/client-key
                                                    :atlassian-connect.host/event-type]) "Installing host")
        id (client-key->crux-id (:atlassian-connect.host/client-key lifecycle-payload))
        db (crux/db crux-node)
        prev-lifecycle (crux/entity db id)
        tx-result (crux/submit-tx
                   crux-node
                   [[:crux.tx/cas
                     prev-lifecycle
                     (merge
                      prev-lifecycle
                      (assoc lifecycle-payload
                             :crux.db/id id))]])]
    (crux/sync crux-node (:crux.tx/tx-time tx-result) (Duration/ofSeconds 10))))

(defn get-installation [crux-db client-key]
  (crux/entity crux-db (client-key->crux-id client-key->crux-id)))
