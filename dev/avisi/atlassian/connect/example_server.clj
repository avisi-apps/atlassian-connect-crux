(ns avisi.atlassian.connect.example-server
  (:require [reitit.ring :as ring]
            [avisi.atlassian.connect.example-atlassian-connect :as atlassian-connect]
            [avisi.atlassian.connect.interceptors :as connect-interceptors]
            [mount.core :refer [defstate]]
            [ring.adapter.jetty :as jetty]
            [reitit.http :as http]
            [muuntaja.core :as muuntaja-core]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :as multipart]
            [avisi.atlassian.connect.example-crux :as crux-node]
            [clojure.tools.logging :as log])
  (:import [org.eclipse.jetty.server Server]))

(defn atlassian-connect-handler [_]
  {:status 200
   :body atlassian-connect/edn})

(def with-crux-node-interceptor
  {:enter (fn [ctx]
            (assoc-in ctx [:request :crux-node] crux-node/node))
   :name ::with-crux-node})

(defn routes []
  [["/connect/atlassian-connect.json" {:name ::descriptor
                                       :get atlassian-connect-handler}]
   ["/connect/lifecycle/:lifecycle" {:name ::lifecycle
                                     :post {:interceptors [with-crux-node-interceptor
                                                           (connect-interceptors/lifecycle-interceptor
                                                            {:crux-node-kw :crux-node
                                                             :payload-kw :body-params})]}}]
   ["/assets/*" (ring/create-resource-handler)]])

(defn ex-handler [message exception request]
  (log/error exception message {:api "REST"
                                :request-method (:request-method request)
                                :query-string (:query-string request)
                                :uri (:uri request)})
  {:status 500
   :body {:message message
          :exception (str exception)
          :uri (:uri request)}})

(defn router
  ([]
   (router (routes)))
  ([routes-vec]
   (http/router
    routes-vec
    {:data {:muuntaja muuntaja-core/instance
            :interceptors [(muuntaja/format-negotiate-interceptor)
                           ;; query-params & form-params
                           (parameters/parameters-interceptor)
                           ;; content-negotiation
                           (muuntaja/format-negotiate-interceptor)
                           ;; encoding response body
                           (muuntaja/format-response-interceptor)
                           ;; exception handling
                           (exception/exception-interceptor
                            (merge
                             exception/default-handlers
                             {::exception/default (partial ex-handler "Unexpected exception")}))
                           ;; decoding request body
                           (muuntaja/format-request-interceptor)
                           ;; coercing response bodys
                           (coercion/coerce-response-interceptor)
                           ;; coercing request parameters
                           (coercion/coerce-request-interceptor)
                           ;; multipart
                           (multipart/multipart-interceptor)]}})))


(defn app
  ([]
   (app (router)))
  ([router]
   (http/ring-handler
    router
    (ring/routes
     (ring/redirect-trailing-slash-handler)
     (ring/create-default-handler))
    {:executor sieppari/executor})))

(defn app-handler [{:keys [dev?] :as request}]
  ;; Don't do this in production make sure to make one instance of app an re-use that.
  ((app) request))

(defstate server
          :start (jetty/run-jetty app-handler {:port 3000
                                               :join? false
                                               :async true})
          :stop (.stop ^Server server))

(comment

  (let [db (crux.api/db crux-node/node)]
    (mapv
     #(crux.api/entity db (first %))
     (crux.api/q
      db
      '{:find [?e]
        :where [[?e :atlassian-connect/client-key ?k]]})))

  )
