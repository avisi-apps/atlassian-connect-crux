(ns avisi.atlassian.connect.server
  (:require [reitit.ring :as ring]
            [mount.core :refer [defstate]]
            [ring.adapter.jetty :as jetty]
            [muuntaja.core :as muuntaja-core]
            [reitit.ring.coercion :as coercion]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.ring.spec :as spec]
            [avisi.atlassian.connect.middleware :as middleware]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [clojure.tools.logging :as log])
  (:import [org.eclipse.jetty.server Server]))

(defn atlassian-connect-handler [request]
  {:status 200
   :body (get-in (ring/get-match request) [:data ::atlassian-connect-edn])})

(defn with-built-in-routes [{:keys [routes crux-node atlassian-connect-edn]}]
  (conj
   routes
   ["/connect"
    ["/atlassian-connect.json" {:name ::descriptor
                                ::atlassian-connect-edn atlassian-connect-edn
                                :get atlassian-connect-handler}]
    ["/lifecycle/:lifecycle" {:name ::lifecycle
                              ::crux-node crux-node
                              :post middleware/lifecycle-handler}]]
   ["/assets/*" (ring/create-resource-handler)]))

(defn ex-handler [message exception request]
  (log/error exception message {:api "REST"
                                :request-method (:request-method request)
                                :query-string (:query-string request)
                                :uri (:uri request)})
  {:status 500
   :body {:message message
          :exception (str exception)
          :uri (:uri request)}})

(defn jwt-token-ex-handler [exception request]
  {:status 401
   :body {:message "Invalid jwt token"
          :exception (str exception)
          :uri (:uri request)}})

(defn default-middleware [{:keys [crux-node]}]
  [;; query-params & form-params
   parameters/parameters-middleware
   ;; content-negotiation
   muuntaja/format-negotiate-middleware
   ;; encoding response body
   muuntaja/format-response-middleware
   ;; exception handling
   (exception/create-exception-middleware
    (merge
     exception/default-handlers
     {:invalid-jwt-token jwt-token-ex-handler
      ::exception/default (partial ex-handler "Unexpected exception")}))
   ;; decoding request body
   muuntaja/format-request-middleware
   ;; coercing response bodys
   coercion/coerce-response-middleware
   ;; coercing request parameters
   coercion/coerce-request-middleware
   ;; multipart
   multipart/multipart-middleware
   ;; Crux
   [middleware/with-crux-db crux-node]
   ;; support for jwt validation for request from atlassian or your app
   middleware/wrap-enforce-jwt-validation])

(defn router [routes-vec {:keys [middleware]}]
  (ring/router
   routes-vec
   {:validate spec/validate
    :data {:coercion spec-coercion/coercion
           :muuntaja muuntaja-core/instance
           :middleware middleware}}))

(defn app
  ([router]
   (ring/ring-handler
    router
    (ring/routes
     (ring/redirect-trailing-slash-handler)
     (ring/create-default-handler)))))

(defmacro defhandler
  "defines a ring-handler backed by reitit.
  if you give this handler as option `:dev?` true, it will
  make sure that all the routes are reloadable"
  [sym {:keys [dev?
               routes
               crux-node
               atlassian-connect-edn]}]
  `(def ~sym
     (if ~dev?
        (fn [request#]
           ((app (router (with-built-in-routes
                          {:routes ~routes
                           :crux-node ~crux-node
                           :atlassian-connect-edn ~atlassian-connect-edn})
                         {:middleware (default-middleware {:crux-node ~crux-node})})) request#))
        (app (router (with-built-in-routes
                      {:routes ~routes
                       :crux-node ~crux-node
                       :atlassian-connect-edn ~atlassian-connect-edn})
                     {:middleware (default-middleware {:crux-node ~crux-node})})))))

(comment
  (defstate server
    :start (jetty/run-jetty app-handler {:port 3000
                                         :join? false})
    :stop (.stop ^Server server)))
