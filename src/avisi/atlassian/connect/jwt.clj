(ns avisi.atlassian.connect.jwt
  (:require [buddy.core.codecs :as codecs]
            [clojure.string :as string]
            [cheshire.core :as json]
            [buddy.core.codecs.base64 :as base64]
            [ring.util.codec :as codec]
            [clojure.tools.logging :as log]
            [buddy.sign.jwt :as jwt]
            [buddy.core.hash :as hash]
            [clj-time.core :as time])
  (:import [java.io ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(defn str->input-stream
  [^String s]
  (ByteArrayInputStream. (.getBytes s)))

(defn str->jwt-claims [jwt-string]
  (let [[_ claims _] (string/split jwt-string #"\." 3)]
    (->
     (case (long (mod (count claims) 4))
       2 (str claims "==")
       3 (str claims "=")
       claims)
     (string/replace "-" "+")
     (string/replace "_" "/")
     (base64/decode)
     (codecs/bytes->str)
     (json/parse-string true))))

(defn encode-rfc-3986
  "We follow the same rules as specified in OAuth1:
  Percent-encode everything but the unreserved characters according to RFC-3986:
  unreserved  = ALPHA / DIGIT / - / . / _ / ~
  See http://tools.ietf.org/html/rfc3986"
  [str]
  (string/replace (codec/url-encode str) #"\+" "%2B"))

(defn sort-if-sequential
  [v]
  (str (if (sequential? v)
         (string/join "," (sort (map encode-rfc-3986 v)))
         (encode-rfc-3986 v))))

(defn canonicalize-uri
  "If empty returns / if it ends with a / it will strip it from the end"
  [^String uri]
  (if (or (empty? uri) (= uri "/"))
    "/"
    (if (.endsWith uri "/")
      (subs uri 0 (-> uri count (- 1)))
      uri)))

(defn canonicalize-method
  "Upper cases the method name so get will become GET"
  [method]
  (string/upper-case (name method)))

(defn canonicalize-query-params
  "Sort query params for the hash and keys and values with encode-rfc-3986.
   If one the query params has a list as value it will become comma separated and all the values
   will also become encoded with rfc-3986"
  [m]
  (let [sorted (sort (map (fn [[k v]] [(encode-rfc-3986 (name k)) v]) m))]
    (string/join "&" (map (fn [[k v]] (str k "=" (sort-if-sequential v))) sorted))))

(defn create-qsh
  "Creates a qsh field for the claim in the json webtoken header for more details
  see https://developer.atlassian.com/static/connect/docs/latest/concepts/understanding-jwt.html"
  [method uri query-params]
  (-> (str (canonicalize-method method)
           "&" (canonicalize-uri uri)
           "&" (canonicalize-query-params query-params))
      hash/sha256
      codecs/bytes->hex))

(defn validate-qsh
  "Validate a qsh field"
  [{:keys [qsh] :as decoded-jwt} context]
  (let [path (:uri context)
        method (:request-method context)
        query-params (:query-params context)]
    (log/debug {:query-params query-params
                :decoded-jwt decoded-jwt
                :path path
                :qsh qsh
                :create-qsh (create-qsh method path (dissoc query-params :jwt))}
               "validate-qsh")
    (if (= qsh (create-qsh method path (dissoc query-params :jwt)))
      decoded-jwt
      (throw (ex-info "Query string header does not match" {:query-params query-params
                                                            :method method
                                                            :path path
                                                            :decoded-jwt decoded-jwt
                                                            :error :invalid-qsh})))))

(defn get-jwt-token
  "Gets the JWT token from either the query params jwt or from the authorization header"
  [context]
  (let [headers (:headers context)
        query-params (:query-params context)]
    (or
     (:jwt query-params)
     (last (string/split (get headers "authorization" "") #" ")))))

(defn create-jwt-token [{::keys [issuer method url query-params shared-secret claims]
                         :or {claims {}}}]
  (jwt/sign
   (merge
    {:iss issuer
     :exp (time/plus (time/now) (time/minutes 5))
     :iat (time/now)
     :qsh (create-qsh method url query-params)}
    claims)
   shared-secret))
