(ns avisi.atlassian.connect.pathom
  (:require
    [clojure.spec.alpha :as s]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]))

(s/def ::only-for-jira-admin? boolean?)

(defn jira-admin? [env] (true? (get-in env [:claims :context :admin?])))

(defn get-resolver-params [env]
  (let [k (get-in env [:ast :key])
        sym (get-in env [::pc/indexes ::pc/index-attributes k ::pc/attr-output-in])]
    (reduce (fn [m k] (merge m (get-in env [::pc/indexes ::pc/index-resolvers k]))) {} sym)))

(def atlassian-connect-plugin
  {::p/wrap-read
     (fn [reader]
       (fn [env]
         (if (and (::only-for-jira-admin? (get-resolver-params env)) (not (jira-admin? env)))
           (throw
             (ex-info
               "No permissions"
               {:error :no-access
                :message "Jira admin rights needed"}))
           (reader env))))
   ::p/wrap-mutate
     (fn [mutate]
       (fn [env k params]
         (if (and (get-in env [::pc/indexes ::pc/index-mutations k ::only-for-jira-admin?]) (not (jira-admin? env)))
           (throw
             (ex-info
               "No permissions"
               {:error :no-access
                :message "Jira admin rights needed"}))
           (mutate env k params))))})
