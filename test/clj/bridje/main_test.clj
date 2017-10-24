(ns bridje.main-test
  (:require [bridje.main :as sut]
            [bridje.runtime :as rt]
            [bridje.fake-io :refer [fake-file fake-io]]
            [clojure.string :as s]
            [clojure.test :as t]
            [clojure.walk :as w])
  (:import [bridje.runtime ADT]))

(defn without-loc [v]
  (w/postwalk (fn [v]
                (cond-> v
                  (instance? ADT v) (update :params dissoc :loc-range)))
              v))

(t/deftest e2e-test
  (let [fake-files {'bridje.foo (fake-file
                                 '(ns bridje.foo)

                                 '(def (flip x y)
                                    [y x])

                                 #_'(defmacro (if-not pred then else)
                                    `(if ~pred
                                       ~else
                                       ~then))

                                 '(defdata Nothing)
                                 '(defdata (Just a))
                                 '(defdata (Mapped #{a b})))

                    'bridje.bar (fake-file
                                 '(ns bridje.bar
                                    {aliases {foo bridje.foo}})

                                 '(def (main args)
                                    (let [seq ["ohno"]
                                          just (foo/->Just "just")
                                          nothing foo/Nothing]
                                      {message (foo/flip "World" "Hello")
                                       seq seq
                                       mapped (foo/->Mapped {a "Hello", b "World"})
                                       the-nothing nothing
                                       empty? ((clj empty?) seq)
                                       just just
                                       justtype (match just
                                                       foo/Just "it's a just"
                                                       foo/Nothing "it's nothing"
                                                       "it's something else")
                                       loop-rec (loop [x 5
                                                       res []]
                                                  (if ((clj zero?) x)
                                                    res
                                                    (recur ((clj dec) x)
                                                           ((clj conj) res x))))
                                       justval (foo/Just->a just)})))}

        {:keys [compiler-io !compiled-files]} (fake-io {:source-files fake-files})]

    (bridje.compiler/compile! 'bridje.bar {:io compiler-io, :env {}})

    (t/is (= (sut/run-main {:main-ns 'bridje.bar}
                           {:io compiler-io})

             {:message ["Hello" "World"],
              :seq ["ohno"],
              :empty? false
              :the-nothing (rt/->ADT 'bridje.foo/Nothing {}),
              :mapped (rt/->ADT 'bridje.foo/Mapped {:a "Hello", :b "World"}),
              :just (rt/->ADT 'bridje.foo/Just {:a "just"}),
              :justtype "it's a just"
              :loop-rec [5 4 3 2 1]
              :justval "just"}))))

(t/deftest quoting-test
  (let [{:keys [compiler-io !compiled-files]} (fake-io {:source-files {'bridje.baz (s/join "\n" [(pr-str '(ns bridje.baz))
                                                                                                 "(def simple-quote '(foo 4 [2 3]))"
                                                                                                 (pr-str '(def (main args)
                                                                                                            {simple-quote simple-quote}))])}})]

    (bridje.compiler/compile! 'bridje.baz {:io compiler-io})

    (t/is (= (-> (sut/run-main {:main-ns 'bridje.baz} {:io compiler-io})
                 (without-loc))

             {:simple-quote (rt/->ADT 'bridje.kernel.forms/ListForm,
                                      {:forms [(rt/->ADT 'bridje.kernel.forms/SymbolForm {:sym 'foo})
                                               (rt/->ADT 'bridje.kernel.forms/IntForm {:number 4})
                                               (rt/->ADT 'bridje.kernel.forms/VectorForm {:forms [(rt/->ADT 'bridje.kernel.forms/IntForm {:number 2})
                                                                                                  (rt/->ADT 'bridje.kernel.forms/IntForm {:number 3})]})]})}))))
