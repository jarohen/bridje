(ns bridje.util
  (:require [clojure.string :as s]))

(defn sub-forms [form]
  (conj (case (:form-type form)
          (:vector :list :set) (mapcat sub-forms (:forms form))
          :quote (sub-forms (:form form))
          :record (mapcat sub-forms (map second (:entries form)))
          [])
        form))

(defn sub-exprs [expr]
  (conj (case (:expr-type expr)
          (:vector :set :call :recur :do) (mapcat sub-exprs (:exprs expr))
          :record (mapcat sub-exprs (map second (:entries expr)))
          :if (mapcat (comp sub-exprs expr) #{:pred-expr :then-expr :else-expr})
          :case (concat (sub-exprs (:expr expr))
                        (mapcat (comp sub-exprs :expr) (:clauses expr)))
          (:let :loop) (concat (mapcat sub-exprs (map second (:bindings expr)))
                               (sub-exprs (:body-expr expr)))
          :fn (sub-exprs (:body-expr expr))
          :handling (concat (into [] (comp (mapcat :handler-exprs) (mapcat sub-exprs)) (:handlers expr))
                            (sub-exprs (:body-expr expr)))
          [])
        expr))
