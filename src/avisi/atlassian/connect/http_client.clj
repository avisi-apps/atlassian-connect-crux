(ns avisi.atlassian.connect.http-client
  (:require [avisi.atlassian.connect.jwt :as jwt]
            [clj-http.client :as http]
            [clojure.spec.alpha :as s]
            [cambium.core :as log]
            [clojure.string :as str]))

(s/def ::endpoint #(str/starts-with? % "/"))
(s/def ::method #{:get :post :put :head :delete})
(s/def ::query-params map?)
(s/def ::headers map?)
(s/def ::body any?)

(defn jwt-auth-header [{:keys [host]
                        ::keys [method endpoint query-params]}]
  (let [addon-key (:atlassian-connect.host/key host)
        shared-secret (:atlassian-connect.host/shared-secret host)]
    (str "JWT " (jwt/create-jwt-token {::jwt/iss addon-key
                                       ::jwt/method (or method :get)
                                       ::jwt/url endpoint
                                       ::jwt/query-params query-params
                                       ::jwt/shared-secret shared-secret}))))

(defn client [{::keys [endpoint
                       method
                       body
                       query-params
                       headers]
               :keys [host]
               :or {method :get} :as env}]
  (let [auth-header (jwt-auth-header env)
        base-url (:atlassian-connect.host/base-url host)]
    (log/with-logging-context {:endpoint endpoint
                               :client ::client
                               :method method}
      (->
       (http/request (cond-> {:request-method method
                              :url (str base-url endpoint)
                              :as :json
                              :content-type :json
                              :throw-entire-message? true
                              :headers {"Authorization" auth-header}}
                             headers (update :headers merge headers)
                             query-params (assoc :query-params query-params)
                             body (assoc :form-params body)))
       :body))))

(comment
  (client
   {:host (dev/get-install)
    ::endpoint "/rest/api/2/project/search"})
  )
