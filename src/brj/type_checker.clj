(ns brj.type-checker)

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
  (defn type-apply-mapping [[type & args :as t] m]
    (case type
      :vector `(:vector ~(type-apply-mapping (first args) m))
      :mono-tv (or (get m (first args)) t)
      :lub `(:lub ~@(map #(type-apply-mapping % m) args))
      t))

  (defn mapping-apply-mapping [m1 m2]
    (merge (->> m1 (into {} (map (juxt key (comp #(type-apply-mapping % m2) val)))))
           m2))

  (defn constraint-apply-mapping [[c & args] m]
    (case c
      :lub `(:lub ~@(map #(type-apply-mapping % m) args))))

  (defn constraints-apply-mapping [c m]
    (->> c (into #{} (map #(constraint-apply-mapping % m)))))

  (defn try-solve-constraint [[c & args :as constraint]]
    (case c
      :lub (let [[[t1-type :as t1] [t2-type :as t2] [_ o]] args]
             (cond
               (= t1 t2) [{o t1} nil]
               (and (= :mono-tv t1-type) (= :mono-tv t2-type)) [{} constraint]
               (= :mono-tv t2-type) (recur `(:lub ~t2 ~t1 (:mono-tv ~o)))
               (= t1-type t2-type) (case t1-type
                                     :vector (let [tv (MonoTv.)]
                                               [{o `(:vector (:mono-tv ~tv))}
                                                `(:lub ~(second t1) ~(second t2) (:mono-tv ~tv))]))
               :else (case t2-type
                       :str [{(second t1) `(:str), o `(:str)} nil])))))

  (defn try-solve-constraints [constraints orig-mapping]
    (loop [progress? false
           [c & more-cs] constraints
           next-constraints #{}
           mapping orig-mapping]
      (if-not c
        (if progress?
          (recur false (-> next-constraints (constraints-apply-mapping mapping)) #{} mapping)
          [mapping next-constraints (not= orig-mapping mapping)])

        (let [[new-mapping c'] (try-solve-constraint c)]
          (recur (or progress? (not= c c'))
                 more-cs
                 (cond-> next-constraints c' (conj c'))
                 (mapping-apply-mapping mapping new-mapping))))))

  (defn unify-eq [[[t1-type :as t1] [t2-type :as t2] :as eq]]
    (cond
      (= t1 t2) [{} []]
      (= :mono-tv t1-type) (let [[_ mtv] t1]
                             [{mtv t2} []])
      (= :mono-tv t2-type) [{} [[t2 t1]]]
      :else (throw (ex-info "can't unify" {:eq [t1 t2]}))))

  (defn solve [{:keys [eqs constraints]}]
    (loop [mapping {}
           constraints constraints
           eqs eqs
           mapping-updated? true]
      (let [[mapping constraints mapping-updated?]
            (if-not mapping-updated?
              [mapping constraints false]
              (try-solve-constraints constraints mapping))

            [eq & more-eqs] (->> eqs
                                 (map (fn [[t1 t2]]
                                        [(-> t1 (type-apply-mapping mapping))
                                         (-> t2 (type-apply-mapping mapping))])))]
        (if-not eq
          [mapping constraints]

          (let [[new-mapping extra-eqs] (unify-eq eq)
                mapping-updated? (not= new-mapping {})
                mapping (cond-> mapping
                          mapping-updated? (mapping-apply-mapping new-mapping))]
            (recur mapping
                   (cond-> constraints
                     mapping-updated? (constraints-apply-mapping mapping))
                   (concat extra-eqs more-eqs)
                   mapping-updated?))))))

  ;; by solving constraints, we solve type vars too
  ;; which implies adding to the mapping
  ;; when we add to the mapping, we have to map over both the constraints and the eqs

  (defn combine-typings [ret {:keys [typings eqs constraints]}]
    (let [mono-envs (into [] (mapcat :mono-env) typings)
          lv-mapping (->> (into #{} (map key) mono-envs)
                          (into {} (map (juxt identity (fn [_] `(:mono-tv ~(MonoTv.)))))))
          eqs (concat eqs (for [[lv t] mono-envs]
                            [(get lv-mapping lv) t]))
          [mapping constraints] (solve {:eqs eqs, :constraints constraints})]
      {:ret (-> ret (type-apply-mapping mapping))
       :mono-env (->> lv-mapping
                      (into {} (map (fn [[lv [_ mtv :as t]]]
                                      [lv (get mapping mtv t)]))))
       :constraints constraints}))

  (defn ast-typing [[op & args :as expr]]
    (case op
      (:int :str :bool) {:ret `(~op)}

      :local (let [[local] args
                   ret `(:mono-tv ~(MonoTv.))]
               {:ret ret
                :mono-env {local ret}})

      :vector (let [el-tv `(:mono-tv ~(MonoTv.))
                    typings (mapv ast-typing args)]
                (combine-typings `(:vector ~el-tv)
                                 (merge {:typings typings}
                                        (case (count typings)
                                          0 {}
                                          1 {:eqs [[el-tv (:ret (first typings))]]}
                                          (let [[first-el-type & more-el-types] (map :ret typings)]
                                            {:constraints (->> more-el-types
                                                               (into #{} (map (fn [el-type]
                                                                                `(:lub ~first-el-type ~el-type ~el-tv)))))})))))

      :if (let [[pred-typing then-typing else-typing] (map ast-typing args)
                ret `(:mono-tv ~(MonoTv.))]
            (combine-typings ret
                             {:typings [pred-typing then-typing else-typing]
                              :eqs [['(:bool) (:ret pred-typing)]]
                              :constraints #{`(:lub ~(:ret then-typing)
                                                    ~(:ret else-typing)
                                                    ~ret)}}))))

  (ast-typing `(:if (:local ~'x) (:vector) (:vector (:str "foo") (:str "str")))))

(comment
  (ast-typing `(:if (:local ~'x) (:str "foo") (:str "str"))))

;; interesting things, then
;; call - how does subsumption work?
;; type checking
;; simplifying lubs
;; if we have that t is the lub of t1 and [t2], can we add that to a mapping?
;; we could put through a mapping of t1 -> [t3], and t -> [(lub t3 t2)]
;; what can I conj to vectors if normally vectors are lubs? guess it's `(:: (conj [t] u) [(:lub t u)])`

;; which means that assoc is ...
;; well, we're doing assoc via lenses, so it'll be `(:: #{(.assoc i k v o)} (assoc i k v) o)`

;; maybe let's do merge the same way, `(:: #{(.merge l r o)} (merge l r) o)`
;; and then, also, if.
;; `(:: #{(.lub t e o)} (if p t e) o)`
;; functional dependencies of lub - we know that l r -> o
;; functional dependencies of merge - l o -> r, r o -> l, l r -> o

;; merge is what'll likely require the 'meet' type (although only for records)

;; if we have constrained types like this, we can say that the empty vector is of type [a]
;; because `(if m [] [1])` would then have a lub type
;; might be out of the woods on that one, but `(if m {} {:foo 1})`
