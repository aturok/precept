(ns todomvc.macros
    (:require [clara.rules :refer [defrule]]
              [clara.rules.dsl :as dsl]
              [clara.rules.compiler :as com]))
(defmacro def-tuple-session
  "Wrapper around Clara's `defsession` macro.
  Preloads query helpers."
  [name & sources-and-options]
  `(clara.macros/defsession
     ~name
     'todomvc.util
     ~@sources-and-options
     :fact-type-fn ~'(fn [[e a v]] a)
     :ancestors-fn ~'(fn [type] [:all])))

;TODO. use .spec to define schema
(defn binding? [x]
  (println "Is a binding?" x)
  (and
    (symbol? x)
    (= (first (name x)) \?)))

(defn sexpr? [x]
  (println "Is a sexpr?" x)
  (and
    (list? x)))
;(fn? (first x))))

;TODO. use .spec to define schema
(defn value-expr? [x]
  (println "Is a value-expr?" x)
  (println "Type in value-expr test" (type x))
  (and
    (identity x)
    (not= '_ x)
    (not (binding? x))
    (not (sexpr? x))))


(defn variable-bindings [tuple]
  (into {}
    (filter (fn [[k v]] (binding? v))
      {:e (first tuple)
       :a (second tuple)
       :v (last tuple)})))

(defn sexprs [tuple]
  (into {}
    (filter (fn [[k v]] (sexpr? v))
      {:a (second tuple)
       :v (last tuple)})))

(defn positional-value [tuple]
  (into {}
    (filter (fn [[k v]] (value-expr? v))
      {:v (first (drop 2 tuple))})))

(defn parse-as-tuple [expr]
  (let [tuple                          (first expr)
        bindings                       (variable-bindings tuple)
        bindings-and-constraint-values (merge bindings
                                         (sexprs tuple)
                                         (positional-value tuple))
        value-expressions              (positional-value tuple)
        attribute                      (if (keyword? (second tuple)) (second tuple) :all)]
    (println "Tuple: " tuple)
    (println "Variable bindings for form:" bindings)
    (println "Value expressions for form" value-expressions)
    (println "With s-exprs merged:" bindings-and-constraint-values)
    (reduce
      (fn last-one [rule-expr [eav v]]
        (println "K V" eav v)
        (conj rule-expr
          (if (list? v)                                     ; s-expr?
            v
            (list '= v (symbol (name eav))))))
      (vector attribute (vector ['e 'a 'v]))
      bindings-and-constraint-values)))

(defn parse-with-fact-expression [expr]
  (let [fact-expression (take 2 expr)
        expression      (drop 2 expr)]
    (conj (lazy-seq (parse-as-tuple expression))
      (second fact-expression)
      (first fact-expression))))

(defn rewrite-lhs [exprs]
  (mapv (fn main-one [expr]
          (let [leftmost        (first expr)
                op              (keyword? (dsl/ops leftmost))
                fact-expression (and (not (keyword? leftmost))
                                  (not (vector? leftmost))
                                  (binding? leftmost))]
            (cond
              op expr
              fact-expression (parse-with-fact-expression expr)
              :else (parse-as-tuple expr))))
    exprs))

(defmacro def-tuple-rule
  [name & body]
  (let [doc        (if (string? (first body)) (first body) nil)
        body       (if doc (rest body) body)
        properties (if (map? (first body)) (first body) nil)
        definition (if properties (rest body) body)
        {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
        rw-lhs     (rewrite-lhs lhs)]
    `(clara.macros/defrule ~name ~doc ~body ~properties ~rw-lhs ~rhs)))

;(defmacro defaction
;  [name event effect & body]
;  `(defrule ~name
;     (conj `[:exists [event]] ~@body)
;    => (second effect)))
;
;(macroexpand
;  '(defaction foo
;     :ui/toggle-complete-action
;     [:effect [[?e :todo/done :tag]]]
;     [:todo/title [[e a v]] (= ?e e)]))
;

