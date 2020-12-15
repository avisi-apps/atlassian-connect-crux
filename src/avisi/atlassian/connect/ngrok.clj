(ns avisi.atlassian.connect.ngrok
  (:require
    [clj-http.client :as http]
    [clojure.string :as string]))

(defn get-base-url* [url]
  (try
    (let [tunnels (->
                    (http/get
                      url
                      {:accept :json
                       :as :json})
                    (get-in [:body :tunnels]))
          base-url (->>
                     tunnels
                     (filter #(= "https" (:proto %)))
                     first
                     :public_url)]
      base-url)
    (catch Exception e
      (throw (ex-info "Failed determining base-url are you sure ngrok is running?" {:error-code :ngrok-down})))))

(defn get-base-url [] (get-base-url* "http://localhost:4040/api/tunnels"))

(defn get-fulcro-inspect-base-url []
  (string/replace (get-base-url* "http://localhost:4041/api/tunnels") #"https://" ""))
