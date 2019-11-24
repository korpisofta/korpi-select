# korpi-select
[![Build Status](https://travis-ci.com/korpisofta/korpi-select.svg?branch=master)](https://travis-ci.com/korpisofta/korpi-select)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.korpisofta/korpi-select.svg)](https://clojars.org/org.clojars.korpisofta/korpi-select)
> Select is to edn what XPath is to XML.

Select is a Clojure library for easy handling of large documents.
Using select it is easy to pick items from deeply nested maps and vectors. Just like XPath is used to pick elements from XML documents.

*Example 1.*

You have a data structure which is deeply nested and in the leaf level it contains _products_
```clojure
(def db
  {:customers
   {422 {:name "company A"
         :products [{:name "bike" :price 50 :tags ["discount" "gravel"]}
                    {:name "helmet" :price 20}]}
    724 {:name "company B"
         :local true
         :products [{:name "hat" :price 180 :tags ["discount"]}
                    {:name "coat" :price 140}]}}})
```
and you just want to select all _products_
```clojure
(select [:customers ** :products **] db)
```
so you get
```clojure
({:name "bike", :price 50, :tags ["discount" "gravel"]}
 {:name "helmet", :price 20}
 {:name "hat", :price 180, :tags ["discount"]}
 {:name "coat", :price 140})
```

*Example 2.*

You are using a document oriented database and you end up having large object trees. In order to use these trees you write code that looks like
```clojure
(->> db
     (:customers)
     (vals)
     (mapcat #(:products %))
     (keep :price)
     (reduce + 0))
```
Although this kind of code works, it is full of noise. Your business logic is hidden behind technical details. The reader of your code should see what you mean:
```clojure
(select-1 [:customers ** :products ** :price =>sum] db)
```

# Usage

The select API has two functions:
```clojure
(defn select [selectors data])
(defn select-1 [selectors data])
```
`select` returns a sequence and `select-1` takes the first element of the sequence and returns that. `select-1` is handy when combined with reducing selectors, like
```clojure
(select-1 [:customers ** :products ** :price =>sum] db)
```
Reducing selectors have a prefix `=>` and they return one element.

The `data` part is optional in `select` and if it is left out a transducer is returned.

# The API is all about composing transducers

If `select` is called without the data to work on, it returns a [transducer](https://clojure.org/reference/transducers) just like Clojure core library functions `map, take, etc.`. The first argument to select is a vector of transducers or things that can be converted into transducers. So basically `select` is function that composes transducers.

## Converting non-transducers into transducers

There are two predefined automatic conversions into transducers:
1. Functions are converted using `keep`.
  This means that _keywords_ convert so that `:foo` will become `(keep :foo)`
1. Everything else is converted by assuming that it is a key in an associative collection. So _strings_ are converted so that `"foo"` will become `(keep #(get % "foo"))`

## Using transducers as input to select

Since `select` returns a transducer (1-arity) and it takes a vector of transducers as a parameter, *you can compose selects*:
```clojure
(let [customers (select [:customers **])
      products (select [:products **])]
  (select [customers products] db))
```

# Examples

Here are example selects and their equivalent regular Clojure counterparts:

```clojure
;; Select using number or string as a map key
(select [:customers 724] db)
;; without select:
(get-in db [:customers 724])

;; Select all items in a sequence or all values in a map
(select [:customers **] db)
;; without select:
(-> db :customers (vals))

;; Select recursively from all nested maps or sequences
;; This selection method works like `//` -selector in XPath.
(select [-all- :price] db)
;; without select: (but not exactly same)
(->> db :customers (vals) (mapcat :products) (keep :price))

;; Reducing values
;; Reducing transducers have a naming convention: they start with `=>`. There are five predefined transducers: `=>count` `=>sum` `=>vec` `=>first` `=>last`
(select-1 [-all- :price =>sum] db)
;; without select: (but not exactly same)
(->> db :customers (vals) (mapcat :products) (keep :price) (reduce +))

;; Using other functions
;; All non-transducer functions are transformed into transducers using keep, so
(select [:customers ** #(select-keys % [:name])] db)
;; without select:
(->> db :customers (vals) (keep #(select-keys % [:name])))


;; Extracting keys from maps
(select [:customers ** :products ** (values :name :price)] db)
;; without select:
(->> db :customers (vals) (mapcat :products) (mapcat (juxt :name :price)))

;; Pushing the map key into map value
(select [:customers (push-key :company-id)] db)
;; without select:
(->> db :customers (map (fn [[k v]] (assoc v :company-id k))))

;; Pushing the map key into leaf level
(select [:customers (push-key :company-id [:products **])] db)
;; without select:
(for [[k v] (:customers db)
      product (-> v :products)]
  (assoc product :company-id k))

;; Pushing item into leaf level
(select [:customers ** (push :name [:products **])] db)
;; without select:
(for [customer (-> db :customers (vals))
      product (:products customer)]
  (assoc product :name (:name customer)))

;; Pulling items from leaves to branches
;; - select all customers and add an array of product names into them
(select [:customers ** (pull :product-names [:products ** :name =>vec])] db)
;; without select:
(for [customer (-> db :customers (vals))
      :let [product-names (->> customer :products (keep :name) (into []))]]
  (assoc customer :product-names product-names))

;; filtering items based on data on leaf level
;; - select all products where price is greater than 150
(select [:customers ** :products **
         (exists [:price #(> % 150)])] db)
;; without select:
(->> db
     :customers
     (vals)
     (mapcat :products)
     (filter #(> (:price %) 150)))

;; filtering items based on data on leaf level
;; - select all customers who have only expensive products
(select [:customers **
         (all [:products ** :price #(> % 100)])] db)
;; without select:
(->> db
     :customers
     (vals)
     (filter (fn [c] (every? (fn [i] (> (:price i) 100)) (:products c)))))
```




# License

© Korpisofta Oy

Apache License Version 2.0




