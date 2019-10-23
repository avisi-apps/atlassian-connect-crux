(ns avisi.atlassian.connect.http-client
  (:require [avisi.atlassian.connect.jwt :as jwt]
            [clojure.tools.logging :as log]
            [clj-http.client :as http]))

(defn request
  [method host url opts]
  (let [add-on-key (:atlassian-connect.client/key host)
        shared-secret (:atlassian-connect.client/shared-secret host)
        base-url (:atlassian-connect.client/base-url host)
        jwt-auth-header (str "JWT " (jwt/create-jwt-token {::jwt/issuer add-on-key
                                                           ::jwt/method method
                                                           ::jwt/url url
                                                           ::jwt/query-params (:query-params opts {})
                                                           ::jwt/shared-secret shared-secret}))]
    (http/request (-> (assoc opts
                             :request-method method
                             :url (str base-url url)
                             :as :json
                             :content-type :json
                             :throw-entire-message? true)
                      (update-in [:headers] #(assoc % "Authorization" jwt-auth-header))))))

(defn impersonated-request
  [impersonation-token method host url opts]
  (let [bearer-token (str "Bearer " (:access_token impersonation-token))]
    (http/request (-> (assoc opts
                             :request-method method
                             :url (str (:host/base-url host) url)
                             :as :json
                             :content-type :json
                             :throw-entire-message? true)
                      (update-in [:headers] #(assoc % "Authorization" bearer-token))))))

(comment

  (request
   :get
   (dev/get-install)
   "/rest/api/3/project/search"
   {})

  )
