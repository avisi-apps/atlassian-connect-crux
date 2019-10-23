(ns avisi.atlassian.connect.ngrok
  (:require [clj-http.client :as http]))

(defn get-base-url []
  (try
    (let [tunnels (-> (http/get "http://localhost:4040/api/tunnels" {:accept :json
                                                                     :as :json})
                      (get-in [:body :tunnels]))
          base-url (->> tunnels
                        (filter #(= "https" (:proto %)))
                        first
                        :public_url)]
      base-url)
    (catch Exception e
      (throw (ex-info "Failed determining base-url are you sure ngrok is running?"
                      {:error-code :ngrok-down})))))
