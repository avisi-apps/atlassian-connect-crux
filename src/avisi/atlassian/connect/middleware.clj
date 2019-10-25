(ns avisi.atlassian.connect.middleware
  (:require [reitit.ring :as ring]
            [avisi.atlassian.connect.crux :as connect-crux]
            [clojure.spec.alpha :as s]
            [cambium.core :as log]
            [clojure.string :as str]
            [crux.api :as crux]
            [avisi.atlassian.connect.jwt :as jwt]))

(defn host-request
  "Adds host from jwt token to the request, or throws a error when the jwt token is invalid"
  [request {:keys [validate-qsh?]}]
  (let [jwt-token (jwt/get-jwt-token request)
        _ (when (str/blank? jwt-token)
            (throw (ex-info "jwt toking is missing"  {:type :invalid-jwt-token
                                                      :uri (:uri request)
                                                      :query (:query-string request)
                                                      :cause :missing-jwt-token})))
        claims (jwt/str->jwt-claims jwt-token)
        host (connect-crux/get-installation (:crux-db request) (:iss claims))
        request (assoc request
                       :host host
                       :claims claims)]
    (jwt/validate-jwt-token request {:validate-qsh? validate-qsh?
                                     :shared-secret (:atlassian-connect.host/shared-secret host)})
    request))

(s/def ::jwt-validation #{:from-atlassian :from-app})

(def wrap-enforce-jwt-validation
  {:name ::wrap-enforce-jwt-validation
   :spec (s/keys :req-un [::jwt-validation])
   :description "Used to validate incoming jwt tokens from Atlassian or your app"
   :compile (fn [route-data _opts]
              (when-let [jwt-validation (:jwt-validation route-data)]
                (let [options (case jwt-validation
                                :from-atlassian {:validate-qsh? true}
                                :from-app {:validate-qsh? false}
                                nil)]
                  (fn [handler]
                    (fn
                      ([request]
                       (handler (host-request request options)))
                      ([request respond raise]
                       (handler (host-request request options) respond raise)))))))})

(def with-crux-db
  {:name ::with-crux-db
   :wrap (fn [handler crux-node]
           (fn [request]
             (handler (assoc request
                             :crux-node crux-node
                             :crux-db (crux/db crux-node)))))})

(def lifecycle-handler
  {:handler (fn [request]
              (let [{:avisi.atlassian.connect.server/keys [crux-node]} (:data (ring/get-match request))]
                (connect-crux/handle-lifecycle-payload! crux-node (:body-params request))
                {:status 200
                 :body "ok"}))})
