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
