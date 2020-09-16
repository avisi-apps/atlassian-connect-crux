(ns avisi.atlassian.connect.jwt-test
  (:require
    [clojure.test :as t]
    [avisi.atlassian.connect.jwt :as jwt])
  (:import
    [java.io InputStream]))

(t/deftest str->input-stream
  (let [input-stream (jwt/str->input-stream "foo")]
    (t/is (instance? InputStream input-stream))
    (t/is (= "foo" (slurp input-stream)))))
