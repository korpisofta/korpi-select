(ns korpi-select.select-test
  (:require [clojure.test :refer :all]
            [korpi-select.select :refer :all]))

(deftest base-cases
  (testing "keyword"
    (is (= '(3) (select [:a] {:a 3 :b 5}))))

  (testing "other key"
    (is (= '(3) (select ["a"] {"a" 3 "b" 5}))))

  (testing "key not found"
    (is (= () (select [:c] {:a 3 :b 5})))
    (is (= () (select [:a] {:a nil :b 5})))
    (is (= [] (select [1] [1 nil])))
    (is (= [] (select [6] [1 nil]))))

  (testing "nil input"
    (is (= () (select [:c] nil))))

  (testing "**"
    (is (= () (select [**] {})))
    (is (= () (select [**] [])))
    (is (= () (select [**] #{})))
    (is (= [1 2 3] (select [**] [1 nil 2 3])))
    (is (= [3 5] (select [**] {:a 3 :b 5}))))

  (testing "** **"
    (let [data [[1 nil 2 3] nil [] [4 5] [1 nil 2 3]]]
      (is (= [1 2 3 4 5 1 2 3] (select [** **] data)))))

  (testing "access by keys"
    (let [data [{:b [{:id 566 :subs [{:id 633}]}]}]]
      (is (= [633] (select [0 :b 0 :subs 0 :id] data)))))

  (testing "pick just values"
    (is (= [3 8] (select [(values :a :c)] {:a 3 :b 5 :c 8}))))

  (testing "select-1"
    (let [data [{:a 4} {:a 5}]]
      (is (= 4 (select-1 [** :a] data))))))

(def db
  {:customers
   {422 {:name "company A"
         :products [{:name "bike" :price 50 :tags ["discount" "gravel"]}
                    {:name "helmet" :price 20}]}
    724 {:name "company B"
         :local true
         :products [{:name "hat" :price 180 :tags ["discount"]}
                    {:name "coat" :price 140}]}}})

(deftest infinite-input
  (testing "infinite input"
    (is (= ["antti"]
           (select [** :name ** :name =>first]
                   (repeat {:name (repeat {:name "antti"})}))))
    (is (= ["antti"]
           (select [** ** =>first]
                   (repeat (repeat "antti")))))
    (is (= ["antti"]
           (select [-all- :name =>first]
                   (repeat (repeat (repeat {:name "antti"}))))))))

(deftest pipes
  (testing "rename"
    (is (= [{:z 1 :b 2 :c 3}]
           (select [(rename :a :x
                            :x :y
                            :y :z)]
                   {:a 1 :b 2 :c 3})))))

(deftest reducing-transducers
  (let [data {:order [{:customer-id 3
                       :items [{:price 6 :tax 7 :desc "Foo"}
                               {:price 3 :tax 1 :desc "Bar"}
                               {:price 3 :tax 1 :desc "Bar"}]}]}]
    (testing "sum"
      (is (= 9 (select-1 [:order ** :items ** :tax =>sum] data))))

    (testing "count"
      (is (= 3 (select-1 [:order ** :items ** :tax =>count] data))))

    (testing "vec"
      (is (= [6 3 3] (select-1 [:order ** :items ** :price =>vec] data))))

    (testing "=>first"
      (is (= 50 (select-1 [-all- :price =>first] db))))

    (testing "=>last"
      (is (= 140 (select-1 [-all- :price =>last] db))))))

(extend-type clojure.lang.APersistentSet
  Transducerifier
  (as-transducer [item]
    (apply values item)))

(deftest extending-transducerifier-protocol
  (testing "extending protocol for sets"
    (is (= 70
           (select-1 [:customers #{422} :products ** #{:price} =>sum] db)))))

(deftest -all-test
  (testing "-all-"
    (is (= [50 20 180 140]
           (select [-all- :price] db)))))

(deftest push-pull-and-push-key
  (testing "pull"
    (let [all-customers (select [:customers **])
          product-names (pull :product-names [:products ** :name =>vec])]
      (is (= [{:name "company A" :product-names ["bike" "helmet"]}
              {:name "company B" :product-names ["hat" "coat"]}]
             (select [all-customers
                      product-names
                      #(select-keys % [:name :product-names])]
                     db)))))

  (testing "push"
    (is (= [{:company-name "company A" :name "bike"}
            {:company-name "company A" :name "helmet"}
            {:company-name "company B" :name "hat"}
            {:company-name "company B" :name "coat"}]
           (select [:customers **
                    (rename :name :company-name)
                    (push :company-name [:products **])
                    #(select-keys % [:name :company-name])]
                   db))))

  (testing "push-key without select"
    (is (= [{:company-id 422 :name "company A"}
            {:company-id 724 :name "company B"}]
           (select [:customers
                    (push-key :company-id)
                    #(select-keys % [:name :company-id])]
                   db))))

  (testing "push-key with select"
    (is (= [{:company-id 422 :name "bike"}
            {:company-id 422 :name "helmet"}
            {:company-id 724 :name "hat"}
            {:company-id 724 :name "coat"}]
           (select [:customers
                    (push-key :company-id [:products **])
                    #(select-keys % [:name :company-id])]
                   db))))

  (testing "push-key for vectors"
    (is (= [{:id 0 :a "a"}
            {:id 1 :b "b"}]
           (select [(push-key :id)]
                   [{:a "a"} {:b "b"}]))))

  (testing "push-key for maps inside maps"
    (is (= [{:id1 1 :id2 11 :a "a"}
            {:id1 2 :id2 22 :b "b"}]
           (select [(push-key :id1 [(push-key :id2)])]
                   {1 {11 {:a "a"}}
                    2 {22 {:b "b"}}}))))

  (testing "push 2 items at the same time"
    (is (= [{:company-id 422 :total-price 70 :price 50 :name "bike"}
            {:company-id 422 :total-price 70 :price 20 :name "helmet"}
            {:company-id 724 :total-price 320 :price 180 :name "hat"}
            {:company-id 724 :total-price 320 :price 140 :name "coat"}]
           (select [:customers
                    (push-key :company-id)
                    (pull :total-price [:products ** :price =>sum])
                    (push :company-id :total-price [:products **])
                    #(select-keys % [:company-id :total-price :price :name])]
                   db))))

  (testing "pull 3 items at the same time"
    (is (= [{:total-price 70 :count 2}
            {:total-price 320 :count 2}]
           (select [:customers **
                    (pull :total-price [:products ** :price =>sum]
                          :count2 [:products ** :price =>count]
                          :count [:products ** :price =>count])
                    #(select-keys % [:total-price :count])]
                   db)))))

(deftest in-depth-filters
  (testing "exists"
    (is (= [180]
           (select [:customers ** :products **
                    (exists [:price #(> % 150)])
                    :price]
                   db)))
    (is (= ["company A"]
           (select [:customers **
                    (exists [:products ** :price #(< % 130)])
                    :name]
                   db)))
    (is (= [724]
           (select [:customers
                    (push-key :id)
                    (exists [-all- :price #(> % 130)])
                    :id]
                   db)))
    (is (= [422 724]
           (select [:customers
                    (push-key :id)
                    (exists [:products **]) ;; (exists identity [...])
                    :id]
                   db)))
    (is (= [724]
           (select [:customers
                    (push-key :id)
                    (exists [:products ** :price =>sum #(> % 319)])
                    :id]
                   db))))

  (testing "all"
    (testing "sometimes unexpected 'vacuous truth'"
      (is (= [422 724]
             (select [:customers
                      (push-key :id)
                      (all [-all- :not-found #(> % 150)]) ;; query returns empty set
                      :id]
                     db))))
    (is (= []
           (select [:customers
                    (push-key :id)
                    (exists [:products **])
                    (all [-all- :price #(> % 150)])
                    :id]
                   db)))
    (is (= [724]
           (select [:customers
                    (push-key :id)
                    (exists [:products **])
                    (all [-all- :price #(> % 130)])
                    :id]
                   db)))
    (is (= []
           (select [:customers
                    (push-key :id)
                    (exists [:products ** =>count #(> % 0)])
                    (all [:products ** :price #(< % 40)])
                    :id]
                   db)))))

(comment
  (select [:customers ** (exists [:products ** :price #(> % 100)]) :name] db)
  (select [:customers ** :products first :name] db)

  (select [:customers **
           (pull :total-price [:products ** :price =>sum])
           (push :total-price [:products **])
           (pull :tax [:price #(* % 0.2)])
           (pull :price+tax [#{:price :tax} =>sum])
           #(select-keys % [:total-price :price :tax :price+tax])]
          db)

  (select [:customers **
           (pull :total-price [:products ** :price =>sum])
           (push :total-price [:products **])
           (pull :tax [:price (map #(* % 0.2))])
           (pull :price+tax [(values :price :tax) =>sum])
           #(select-keys % [:total-price :price :tax :price+tax])]
          db)

  (select [:customers ** :products **
           (exists [:price #(> % 100)])
           ;(filter #(> (:price %) 100))
           #(select-keys % [:price])]
          db)
  (into []
        (select [:customers ** :products ** #(select-keys % [:price :name])])
        [db])


  (select-1 [:customers ** :products **
             #(select-keys % [:price :name])
             (reducer conj! #(transient []))
             persistent!]
             db)

  (transduce
    (select [:customers ** :products ** #(select-keys % [:price :name])])
    (completing #(conj! %1 %2) persistent!)
    (transient [])
    [db]))
