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
  (defn with-flow-edge [typing [s- s+]]
    (-> typing
        (update-in [:states s- :flow+] (fnil conj #{}) s+)
        (update-in [:states s+ :flow-] (fnil conj #{}) s-)))

  (defn with-transition [typing [l t r]]
    (-> typing
        (update-in [:states l :out t] (fnil conj #{}) r)))

  (defn with-head-class [typing head states]
    (let [hc (gensym 'hc)]
      (as-> typing typing
        (assoc-in typing [:head-classes hc] {:head head, :states states})
        (reduce (fn [typing state]
                  (assoc-in typing [:states :head-class] hc))
                typing
                states))))

  (declare unify-heads)
  (declare combine-heads)
  (declare propagate-head)

  (defn merge+ [typing s+' s+]
    (as-> typing typing
      (update typing :states unify-heads s+' s+)
      (reduce with-transition typing
              (for [[t rs] (get-in typing [:states s+ :out])
                    r rs]
                [s+' t r]))
      (reduce with-flow-edge typing
              (for [s- (get-in typing [:states s+ :flow-])]
                [s- s+']))))

  (defn merge- [typing s-' s-]
    (as-> typing typing
      (update typing :states unify-heads s-' s-)
      (reduce with-transition typing
              (for [[t rs] (get-in typing [:states s- :out])
                    r rs]
                [s-' t r]))
      (reduce with-flow-edge
              typing
              (for [s+ (get-in typing [:states s- :flow+])]
                [s-' s+]))))

  (defn biunify [typing [s+ s-]]
    (as-> typing typing
      (unify-heads typing s+ s-)
      (reduce #(merge+ %1 %2 s+) typing (get-in typing [:states s- :flow+]))

      (reduce #(merge- %1 %2 s-) typing (get-in typing [:states s+ :flow-]))

      (reduce biunify typing
              (for [[t+ rs+] (get-in typing [:states s+ :out])
                    [t- rs-] (get-in typing [:states s- :out])
                    :when (= t+ t-)
                    r+ rs+
                    r- rs-]
                [r+ r-]))))

  (defn combine-typings [ret typings]
    {:ret ret
     :states (reduce (fn [states [sid s]]
                       (let [head (combine-heads s (get states sid))]
                         (-> states
                             (assoc sid (if-let [s2 (get states sid)]
                                          {:head head
                                           :flow- (set/union (:flow- s) (:flow- s2))
                                           :flow+ (set/union (:flow+ s) (:flow+ s2))
                                           :out (merge-with set/union (:out s) (:out s2))}
                                          s))
                             (propagate-head head sid))))
                     {}
                     (mapcat :states typings))
     :locals (into #{} (mapcat :locals) typings)})

  (defn ast-typing [[op & args]]
    (case op
      :bool (let [bool+ (gensym 'bool+)]
              (-> {:ret bool+}
                  (with-head-class :bool #{bool+})))

      :int (let [int+ (gensym 'int+)]
             (-> {:ret int+}
                 (with-head-class :int #{int+})))

      :local (let [[local] args
                   local- (symbol (str local "-"))
                   local+ (gensym (str local "+"))]
               (-> {:ret local+
                    :locals #{local-}}
                   (with-head-class nil #{local- local+})
                   (with-flow-edge [local- local+])))

      :if (let [ret- (gensym 'if-)
                ret+ (gensym 'if+)
                bool- (gensym 'bool-)
                bool-hc (gensym 'hc)
                ret-hc (gensym 'hc)
                [pred-typing then-typing else-typing :as typings] (map ast-typing args)]
            (reduce biunify
                    (-> (combine-typings ret+
                                         (conj typings
                                               {:states {bool- {:head-class bool-hc}
                                                         ret- {:head-class ret-hc}
                                                         ret+ {:head-class ret-hc}}
                                                :head-classes {bool-hc {:states #{bool-}, :head :bool}
                                                               ret-hc {:states #{ret- ret+}}}}))
                        (with-flow-edge [ret- ret+]))
                    #{[(:ret pred-typing) bool-]
                      [(:ret then-typing) ret-]
                      [(:ret else-typing) ret-]}))

      :vector (let [ret (gensym 'vec)
                    ret-hc (gensym 'hc)
                    el- (gensym 'el-)
                    el+ (gensym 'el+)
                    el-hc (gensym 'hc)
                    typings (map ast-typing args)]
                (reduce biunify
                        (-> (combine-typings ret
                                             (conj typings
                                                   (-> {}
                                                       (with-head-class nil #{el- el+})
                                                       (with-head-class :vec #{ret})
                                                       (with-transition [ret 'el el+])
                                                       (with-flow-edge [el- el+])))))
                        (into #{} (map (juxt :ret (constantly el-))) typings)))

      :set (let [ret (gensym 'set)
                 el- (gensym 'el-)
                 el+ (gensym 'el+)
                 typings (map ast-typing args)]
             (reduce biunify
                     (-> (combine-typings ret typings)
                         (assoc-in [:states ret] {:head :set})
                         (with-transition [ret 'el el+])
                         (with-flow-edge [el- el+]))
                     (into #{} (map (juxt :ret (constantly el-))) typings)))))

  ;; TODO functions
  ;; TODO simplification
  ;; TODO I don't want joins of different head types
  ;; and also needs to propagate to all relationships /from/ those nodes
  ;; maybe DFAing would do that?
  (ast-typing '(:if (:local x) (:vector (:local x)) (:vector (:int 25)))))
