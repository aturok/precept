(ns libx.defaction-test
  (:require [libx.macros :refer [defaction]]
            [clojure.test :refer [use-fixtures deftest run-tests is testing]]
            [clara.tools.tracing :as trace]
            [libx.tuplerules :refer [def-tuple-session def-tuple-query]]))


(def fact [1 :foo :tag])

(defaction my-action fact)

(def-tuple-session my-session)

(defn find-insertions [trace]
  (mapcat :facts (:add-facts (group-by :type trace))))

(defn find-retractions [trace]
  (mapcat :facts (:retract-facts (group-by :type trace))))

(deftest defaction-test
  (let [test-session (my-action (trace/with-tracing my-session))]
    (is (= (find-insertions (trace/get-trace test-session))
           (list fact)))
    (is (= (find-retractions (trace/get-trace test-session))
           (list fact)))))


(run-tests)