(ns brj.type-checker)

(defn ->ast
  ([form] (->ast form {:locals {}}))
  ([form {:keys [locals]}]
   (letfn [(->ast' [form]
             (->ast form {:locals locals}))]
     (cond
       (map? form) {:op :record
                    :entries (->> (for [[k v] form]
                                    {:k k, :v (->ast' v)})
                                  vec)}

       (vector? form) {:op :vector
                       :els (into [] (map ->ast') form)}

       (set? form) {:op :vector
                    :els (into #{} (map ->ast') form)}

       (list? form) (case (first form)
                      if (merge {:op :if}
                                (zipmap [:pred-expr :then-expr :else-expr] (map ->ast' (rest form))))
                      let (let [[_ bindings-form & body] form
                                [locals bindings] (reduce (fn [[locals bindings] [sym expr]]
                                                            (let [local (gensym sym)]
                                                              [(assoc locals sym local)
                                                               (conj bindings {:local local
                                                                               :expr (->ast expr {:locals locals})})]))
                                                          [locals []]
                                                          (partition-all 2 bindings-form))]
                            {:op :let
                             :bindings bindings
                             :body (->ast (apply list 'do body) {:locals locals})})

                      fn (let [[_ params & body] form
                               params (mapv (juxt identity gensym))]
                           {:op :fn
                            :params params
                            :body (->ast (apply list 'do body) {:locals (merge locals params)})})

                      do {:op :do
                          :exprs (mapv ->ast' (butlast (rest form)))
                          :expr (->ast' (last form))}

                      {:op :call
                       :f (->ast' (first form))
                       :args (into [] (map ->ast') (rest form))})

       (integer? form) {:op :int, :int form}
       (string? form) {:op :str, :str form}
       (boolean? form) {:op :bool, :bool form}
       (symbol? form) (cond
                        (contains? locals form) {:op :local, :local (get locals form)}
                        :else (throw (ex-info "unknown sym" {:sym form
                                                             :locals locals})))
       :else (throw (ex-info "unknown expr" {:op form
                                             :first (type form)}))))))

(deftype UnknownType [])

(defmethod print-method UnknownType [^UnknownType x w]
  (print-method (format "t%04d" (mod (.hashCode x) 10000)) w))

(do
  (defn type-apply-mapping [[type :as t] m]
    (case type
      :vector (let [[_ el-type] t]
                [:vector (type-apply-mapping el-type m)])
      :unknown (let [[_ ut] t]
                 (or (get m ut) t))
      t))

  (defn mapping-apply-mapping [m1 m2]
    (merge (->> m1 (into {} (map (juxt key (comp #(type-apply-mapping % m2) val)))))
           m2))

  (defn unify-eqs [eqs]
    (loop [mapping {}
           [[[t1-type :as t1] [t2-type :as t2] :as eq] & more-eqs] eqs]
      (cond
        (nil? eq) mapping
        (= t1 t2) (recur mapping more-eqs)
        (= :unknown t2-type) (recur mapping (cons [t2 t1] more-eqs))
        (= :unknown t1-type) (let [[_ ut] t1
                                   mapping (mapping-apply-mapping mapping {ut t2})]
                               (recur mapping
                                      (for [[t1 t2] more-eqs]
                                        [(-> t1 (type-apply-mapping mapping))
                                         (-> t2 (type-apply-mapping mapping))])))
        :else (throw (ex-info "can't unify" {:eq [t1 t2]})))))

  (defn combine-typings [ret {:keys [typings eqs]}]
    (let [mono-envs (into [] (mapcat :mono-env) typings)
          lv-mapping (->> (into #{} (map key) mono-envs)
                          (into {} (map (juxt identity (fn [_] [:unknown (UnknownType.)])))))
          eqs (concat eqs (for [[lv t] mono-envs]
                            [(get lv-mapping lv) t]))
          mapping (unify-eqs eqs)]
      {:ret (-> ret (type-apply-mapping mapping))
       :mono-env (->> lv-mapping
                      (into {} (map (fn [[lv [_ ut :as t]]]
                                      [lv (get mapping ut t)]))))}))

  (defn ast-typing [{:keys [op] :as expr}]
    (case op
      (:int :str :bool) {:ret [op]}

      :local (let [{:keys [local]} expr
                   ret [:unknown (UnknownType.)]]
               {:ret ret
                :mono-env {local ret}})

      :vector (let [{:keys [els]} expr
                    el-type [:unknown (UnknownType.)]
                    typings (mapv ast-typing els)]
                (combine-typings [:vector el-type]
                                 {:typings typings
                                  :eqs (map vector (repeat el-type) (map :ret typings))}))

      :if (let [{:keys [pred-expr then-expr else-expr]} expr
                pred-typing (ast-typing pred-expr)
                then-typing (ast-typing then-expr)
                else-typing (ast-typing else-expr)
                ret [:unknown (UnknownType.)]]
            (combine-typings ret
                             {:typings [pred-typing then-typing else-typing]
                              :eqs [[[:bool] (:ret pred-typing)]
                                    [ret (:ret then-typing)]
                                    [ret (:ret else-typing)]]})))))

(comment
  (ast-typing {:op :if,
               :pred-expr {:op :local, :local 'x},
               :then-expr {:op :str, :str "foo"},
               :else-expr {:op :str, :str "str"}}))
