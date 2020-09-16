(ns avisi.atlassian.connect.crux
  (:require
    [crux.api :as crux]
    [camel-snake-kebab.core :as csk]
    [avisi.atlassian.connect.jwt :as jwt]
    [cambium.core :as log])
  (:import
    [java.time Duration]))

(defn client-key->crux-id [client-key] (keyword "atlassian-connect.host" client-key))

(defn namespace-all-keys
  "Change all keys from camelCase to kebab-case namespace keywords
  Example: clientKey will become :atlassian-connect.host/client-key"
  [m]
  (reduce (fn [m [k v]] (assoc m (keyword "atlassian-connect.host" (csk/->kebab-case (name k))) v)) {} m))

(defn update-host! [{:keys [prev-host new-host crux-node]}]
  (loop [tries 0
         host prev-host]
    (when (= tries 4) (throw (ex-info "Failed handling lifecycle payload" {:amount-of-times-tried tries})))
    (let [id (:crux.db/id new-host)
          tx (crux/submit-tx crux-node [[:crux.tx/cas host (merge host (assoc new-host :crux.db/id id))]])]
      (crux/await-tx crux-node tx (Duration/ofSeconds 10))
      (when-not (crux/tx-committed? crux-node tx) (recur (inc tries) (crux/entity (crux/db crux-node) id))))))

(defn handle-lifecycle-payload! [crux-node request]
  (let [lifecycle-payload (namespace-all-keys (:body-params request))
        _ (log/info
            (select-keys
              lifecycle-payload
              [:atlassian-connect.host/base-url :atlassian-connect.host/client-key :atlassian-connect.host/event-type])
            "Handling host lifecycle call")
        id (client-key->crux-id (:atlassian-connect.host/client-key lifecycle-payload))
        db (crux/db crux-node)
        prev-lifecycle (crux/entity db id)]
    ;; Check JWT token on subsequent calls, this prevents tinkering with shared-secrets of hosts.
    (when prev-lifecycle
      (jwt/validate-jwt-token
        request
        {:validate-qsh? true
         :shared-secret (:atlassian-connect.host/shared-secret prev-lifecycle)}))
    (update-host!
      {:prev-host prev-lifecycle
       :new-host (assoc lifecycle-payload :crux.db/id id)
       :crux-node crux-node})))

(defn get-installation [crux-db client-key] (crux/entity crux-db (client-key->crux-id client-key)))
