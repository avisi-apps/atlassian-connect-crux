(ns avisi.atlassian.connect.interceptors
  (:require
    [reitit.ring :as ring]
    [avisi.atlassian.connect.firestore :as connect-firestore]
    [clojure.spec.alpha :as s]
    [cambium.core :as log]
    [clojure.string :as str]
    [avisi.atlassian.connect.jwt :as jwt]
    [ring.util.response :as response]))

(defn- host-request
  "Adds host from jwt token to the request, or throws a error when the jwt token is invalid"
  [request {:keys [validate-qsh?]}]
  (let [jwt-token (jwt/get-jwt-token request)
        _ (when (str/blank? jwt-token)
            (throw
              (ex-info
                "jwt toking is missing"
                {:type :invalid-jwt-token
                 :uri (:uri request)
                 :query (:query-string request)
                 :cause :missing-jwt-token})))
        claims (jwt/str->jwt-claims jwt-token)
        host (connect-firestore/get-installation (:firestore request) (:iss claims))
        request (assoc request
                  :host host
                  :claims claims)]
    (jwt/validate-jwt-token
      request
      {:validate-qsh? validate-qsh?
       :shared-secret (:atlassian-connect.host/shared-secret host)})
    request))

(s/def ::jwt-validation #{:jwt-validation/from-atlassian :jwt-validation/from-app})

(defn atlassian-host-interceptor-ex-handler
  [{:keys [request error]
    :as ctx}]
  (if (= :invalid-jwt-token (:type (ex-data error)))
    (do
      (log/warn
        {:api "REST"
         :request-method (:request-method request)
         :query-string (:query-string request)
         :uri (:uri request)}
        error
        "Invalid jwt token detected")
      (->
        ctx
        (dissoc :error)
        (assoc
          :response
            {:status 401
             :body
               {:message "invalid jwt token"
                :exception (ex-data error)
                :uri (:uri request)}})))
    ctx))

(defn atlassian-host-interceptor []
  {:name ::atlassian-host-interceptor
   :spec (s/keys :req-un [::jwt-validation])
   :description "Used to validate incoming jwt tokens from Atlassian or your app and to add the host to requests"
   :error atlassian-host-interceptor-ex-handler
   :compile
     (fn [route-data _opts]
       (when-let [jwt-validation (:jwt-validation route-data)]
         (let [options (case jwt-validation
                         :jwt-validation/from-atlassian {:validate-qsh? true}
                         :jwt-validation/from-app {:validate-qsh? false}
                         nil)]
           {:enter (fn [ctx] (update ctx :request host-request options))})))})

(defn invalid-license? [{:keys [query-params]}] (not= (get query-params "lic") "active"))

(defn atlassian-marketplace-license-ex-handler [invalid-license-html]
  (fn
    [{:keys [request error]
      :as ctx}]
    (if (= :invalid-license (:type (ex-data error)))
      (do
        (log/warn
          {:request-method (:request-method request)
           :query-string (:query-string request)
           :uri (:uri request)}
          error
          "Invalid license detected")
        (->
          ctx
          (dissoc :error)
          (assoc
            :response
              (->
                invalid-license-html
                response/response
                (response/status 403)
                (response/content-type "text/html")
                (response/header "Content-Security-Policy" "")))))
      ctx)))

(defn check-license? [{:keys [jwt-validation]}] (= jwt-validation :jwt-validation/from-atlassian))

(defn validate-license-interceptor [{:keys [skip-license-check? invalid-license-html]}]
  {:name ::validate-license-interceptor
   :error (atlassian-marketplace-license-ex-handler invalid-license-html)
   :compile
     (fn [route-data _]
       (when (and (not skip-license-check?) (check-license? route-data))
         {:enter
            (fn
              [{:keys [request]
                :as ctx}]
              (if (invalid-license? request) (throw (ex-info "Invalid license" {:type :invalid-license})) ctx))}))})

(def lifecycle-handler
  {:handler
     (fn [request]
       (let [{:avisi.atlassian.connect.server/keys [firestore]} (:data (ring/get-match request))]
         (connect-firestore/handle-lifecycle-payload! firestore request)
         {:status 200
          :body "ok"}))})

(defn dev-interceptor [dev?]
  {:name ::dev
   :enter (fn [ctx] (update ctx :request assoc :dev? dev?))})

(defn firestore-interceptor [firestore]
  {:name ::firestore
   :enter (fn [ctx] (update ctx :request assoc :firestore firestore))})
