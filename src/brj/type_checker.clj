(ns brj.type-checker
  (:require [clojure.set :as set])
  (:import (clojure.lang MapEntry)))

(defn ->ast
  ([form] (->ast form {:locals {}}))
  ([form {:keys [locals]}]
   (letfn [(->ast' [form]
             (->ast form {:locals locals}))]
     (cond
       (map? form) `(:record ~(->> form (into {} (map (juxt key (comp ->ast' val))))))

       (vector? form) `(:vector ~@(into [] (map ->ast') form))

       (set? form) `(:set (into #{} (map ->ast') form))

       (list? form) (case (first form)
                      if `(:if ~@(map ->ast' (rest form)))
                      let (let [[_ bindings-form & body] form
                                [locals bindings] (reduce (fn [[locals bindings] [sym expr]]
                                                            (let [local (gensym sym)]
                                                              [(assoc locals sym local)
                                                               (conj bindings [local (->ast expr {:locals locals})])]))
                                                          [locals []]
                                                          (partition-all 2 bindings-form))]
                            `(:let [~@bindings]
                                   ~(->ast (apply list 'do body) {:locals locals})))

                      fn (let [[_ params & body] form
                               params (mapv (juxt identity gensym))]
                           `(:fn [~@params]
                                 ~(->ast (apply list 'do body)
                                         {:locals (merge locals params)})))

                      do `(:do [~@(mapv ->ast' (butlast (rest form)))] ~(->ast' (last form)))

                      `(:call (->ast' (first form)) ~@(into [] (map ->ast') (rest form))))

       (integer? form) `(:int ~form)
       (string? form) `(:str ~form)
       (boolean? form) `(:bool ~form)
       (symbol? form) (cond
                        (contains? locals form) `(:local ~(get locals form))
                        :else (throw (ex-info "unknown sym" {:sym form
                                                             :locals locals})))
       :else (throw (ex-info "unknown expr" {:op form
                                             :first (type form)}))))))

(deftype MonoTv [])

(defmethod print-method MonoTv [^MonoTv x w]
  (print-method (format "t%04d" (mod (.hashCode x) 10000)) w))

(do
  (defn unify-head [{s1-head :head, :as s1} {s2-head :head, :as s2}]
    (when (and s1-head s2-head
               (not= s1-head s2-head))
      (throw (ex-info "failed to unify"
                      {:s1 s1, :s2 s2})))
    (or s1-head s2-head))

  (defn with-flow-edge [typing [s- s+]]
    (-> typing
        (update-in [:states s- :flow+] (fnil conj #{}) s+)
        (update-in [:states s+ :flow-] (fnil conj #{}) s-)))

  (defn biunify [typing [s+ s-]]
    (clojure.pprint/pprint typing)

    (unify-head (get-in typing [:states s+])
                (get-in typing [:states s-]))

    (as-> typing typing
      (reduce (fn [typing s+']
                (as-> typing typing
                  (assoc-in typing [:states s+' :head]
                            (unify-head (get-in typing [:states s+'])
                                        (get-in typing [:states s+])))
                  (reduce with-flow-edge
                          typing
                          (for [s- (get-in typing [:states s+ :flow-])]
                            [s- s+']))))
              typing
              (get-in typing [:states s- :flow+]))

      (reduce (fn [typing s-']
                (as-> typing typing
                  (assoc-in typing [:states s-' :head]
                            (unify-head (get-in typing [:states s-'])
                                        (get-in typing [:states s-])))
                  (reduce with-flow-edge
                          typing
                          (for [s+ (get-in typing [:states s- :flow+])]
                            [s-' s+]))))
              typing
              (get-in typing [:states s+ :flow-]))))

  (defn combine-typings [ret typings]
    {:ret ret
     :states (->> (map :states typings)
                  (apply merge-with (fn [s1 s2]
                                      {:flow- (set/union (:flow- s1) (:flow- s2))
                                       :flow+ (set/union (:flow+ s1) (:flow+ s2))
                                       :head (unify-head s1 s2)})))
     :locals (into #{} (mapcat :locals) typings)})

  (defn ast-typing [[op & args]]
    (case op
      :bool (let [bool+ (gensym 'bool+)]
              {:ret bool+
               :states {bool+ {:head :bool}}})

      :int (let [int+ (gensym 'int+)]
             {:ret int+
              :states {int+ {:head :int}}})

      :local (let [[local] args
                   local- (symbol (str local "-"))
                   local+ (gensym (str local "+"))]
               (-> {:ret local+
                    :locals #{local-}}
                   (with-flow-edge [local- local+])))

      :if (let [ret- (gensym 'if-)
                ret+ (gensym 'if+)
                bool- (gensym 'bool-)
                [pred-typing then-typing else-typing :as typings] (map ast-typing args)]
            (reduce biunify
                    (-> (combine-typings ret+ typings)
                        (assoc-in [:states bool-] {:head :bool})
                        (with-flow-edge [ret- ret+]))
                    #{[(:ret pred-typing) bool-]
                      [(:ret then-typing) ret-]
                      [(:ret else-typing) ret-]}))))

  (ast-typing '(:if (:local y) (:int 25) (:local x))))
