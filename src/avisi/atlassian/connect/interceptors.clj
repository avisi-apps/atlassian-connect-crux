(ns avisi.atlassian.connect.interceptors
  (:require [avisi.atlassian.connect.crux :as connect-crux]
            [reitit.ring :as ring]))

(def lifecycle-interceptor
  {:name ::lifecycle-interceptor
   :enter (fn [{:keys [request] :as ctx}]
            (let [{:avisi.atlassian.connect.server/keys [crux-node]} (:data (ring/get-match request))]
              (connect-crux/handle-lifecycle-payload! crux-node
                                                      (:body-params request))
              (assoc ctx :response {:status 200
                                    :body "ok"})))})
