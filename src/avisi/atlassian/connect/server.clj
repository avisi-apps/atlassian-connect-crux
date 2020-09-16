(ns avisi.atlassian.connect.server
  (:require [reitit.ring :as ring]
            [reitit.http :as http]
            [mount.core :refer [defstate]]
            [ring.adapter.jetty :as jetty]
            [muuntaja.core :as muuntaja-core]
            [reitit.http.coercion :as coercion]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.ring.spec :as spec]
            [avisi.atlassian.connect.interceptors :as middleware]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :as multipart]
            [cambium.core :as log])
  (:import [org.eclipse.jetty.server Server]))

(defn atlassian-connect-handler [request]
  {:status 200
   :body (get-in (http/get-match request) [:data ::atlassian-connect-edn])})

(defn with-built-in-routes [{:keys [routes crux-node atlassian-connect-edn]}]
  (conj
   routes
   ["/connect"
    ["/atlassian-connect.json" {:name ::descriptor
                                ::atlassian-connect-edn atlassian-connect-edn
                                :get {:handler atlassian-connect-handler}}]
    ["/lifecycle/:lifecycle" {:name ::lifecycle
                              ::crux-node crux-node
                              :post middleware/lifecycle-handler}]]
   ["/assets/*" (ring/create-resource-handler)]))

(defn ex-handler [message exception request]
  (log/error {:api "REST"
              :request-method (:request-method request)
              :query-string (:query-string request)
              :uri (:uri request)} exception message)
  {:status 500
   :body {:message message
          :exception (str exception)
          :uri (:uri request)}})

(defn default-interceptors [{:keys [crux-node dev? skip-license-check? invalid-license-html]}]
  [;; query-params & form-params
   (parameters/parameters-interceptor)
   ;;; Content-negotiation
   (muuntaja/format-interceptor)
   ;; Exception handling
   (exception/exception-interceptor
    (merge
     exception/default-handlers
     {::exception/default (partial ex-handler "Unexpected exception")}))
   ;; coercing response bodies
   (coercion/coerce-response-interceptor)
   ;; coercing request parameters
   (coercion/coerce-request-interceptor)
   ;; multipart
   (multipart/multipart-interceptor)
   ;; Add dev stuff
   (middleware/dev-interceptor dev?)
   ;; License check
   (middleware/validate-license-interceptor
     {:skip-license-check? skip-license-check?
      :invalid-license-html invalid-license-html})
   ;; Crux
   (middleware/crux-db-interceptor crux-node)
   ;; support for jwt validation for request from atlassian or your app
   (middleware/atlassian-host-interceptor)])

(defn router [routes-vec {:keys [interceptors]}]
  (http/router
   routes-vec
   {:validate spec/validate
    :data {:coercion spec-coercion/coercion
           :muuntaja muuntaja-core/instance
           :interceptors interceptors}}))

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
               atlassian-connect-edn
               skip-license-check?
               invalid-license-html]
        :or {skip-license-check? false}}]
  `(defstate ~sym
     :start (if ~dev?
              (fn [& args#]
                (apply (app (router (with-built-in-routes
                                     {:routes ~routes
                                      :crux-node ~crux-node
                                      :atlassian-connect-edn ~atlassian-connect-edn})
                                    {:interceptors (default-interceptors {:crux-node ~crux-node
                                                                          :dev? ~dev?
                                                                          :skip-license-check? ~skip-license-check?
                                                                          :invalid-license-html ~invalid-license-html})})) args#))
              (app (router (with-built-in-routes
                            {:routes ~routes
                             :crux-node ~crux-node
                             :atlassian-connect-edn ~atlassian-connect-edn})
                           {:interceptors (default-interceptors {:crux-node ~crux-node
                                                                 :dev? ~dev?
                                                                 :skip-license-check? ~skip-license-check?
                                                                 :invalid-license-html ~invalid-license-html})})))))

(comment
  (defstate server
    :start (jetty/run-jetty app-handler {:port 3000
                                         :join? false})
    :stop (.stop ^Server server)))
