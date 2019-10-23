(ns avisi.atlassian.connect.interceptors
  (:require [avisi.atlassian.connect.crux :as connect-crux]))

(defn lifecycle-interceptor [{:keys [crux-node-kw payload-kw]}]
  (letfn [(enter [{:keys [request] :as ctx}]
            (connect-crux/handle-lifecycle-payload! (crux-node-kw request)
                                                    (payload-kw request))
            (assoc ctx :response {:status 200
                                  :body "ok"}))]
    {:name ::lifecycle-interceptor
     :enter enter}))
