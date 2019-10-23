(ns avisi.atlassian.connect.example-atlassian-connect
  (:require [mount.core :refer [defstate]]
            [avisi.atlassian.connect.ngrok :as ngrok]))

(defstate edn :start (let [base-url (ngrok/get-base-url)]
                       {:name "Crux lifecycle"
                        :key "nl.avisi.crux.lifecycle"
                        :description "Lifecycle example"
                        :vendor {:name "Avisi"
                                 :url "http://www.avisi.com"}
                        :links {:self (str base-url "/connect/atlassian-connect.json")}
                        :enableLicensing true
                        :lifecycle {:installed "/connect/lifecycle/installed"
                                    :uninstalled "/connect/lifecycle/uninstalled"
                                    :enabled "/connect/lifecycle/enabled"
                                    :disabled "/connect/lifecycle/disabled"}
                        :baseUrl base-url
                        :authentication {:type "jwt"}
                        :scopes ["act_as_user" ;; Needed for user impersonation
                                 "read"]}))
