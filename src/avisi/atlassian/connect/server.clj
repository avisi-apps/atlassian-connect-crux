(ns avisi.atlassian.connect.server
  (:require [reitit.ring :as ring]
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
                              :post {:interceptors [connect-interceptors/lifecycle-interceptor]}}]]
   ["/assets/*" (ring/create-resource-handler)]
   ))

(defn ex-handler [message exception request]
  (log/error exception message {:api "REST"
                                :request-method (:request-method request)
                                :query-string (:query-string request)
                                :uri (:uri request)})
  {:status 500
   :body {:message message
          :exception (str exception)
          :uri (:uri request)}})

(def default-interceptors
  [(muuntaja/format-negotiate-interceptor)
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
   (multipart/multipart-interceptor)])

(defn router
  ([routes-vec]
   (http/router
    routes-vec
    {:data {:muuntaja muuntaja-core/instance
            :interceptors default-interceptors}})))


(defn app
  ([router]
   (http/ring-handler
    router
    (ring/routes
     (ring/redirect-trailing-slash-handler)
     (ring/create-default-handler))
    {:executor sieppari/executor})))

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
                           :atlassian-connect-edn ~atlassian-connect-edn}))) request#))
        (app (router ~routes)))))

(comment
  (defstate server
    :start (jetty/run-jetty app-handler {:port 3000
                                         :join? false
                                         :async true})
    :stop (.stop ^Server server)))
