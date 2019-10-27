# korpi-select [![Build Status](https://travis-ci.com/korpisofta/korpi-select.svg?branch=master)](https://travis-ci.com/korpisofta/korpi-select)
> Select is to edn what XPath is to XML.

Select is a Clojure library for easy handling of large documents. 
Using select it is easy to pick items from deeply nested maps and vectors. Just like XPath is used to pick elements from XML documents.

Example: you have a data structure which is deeply nested and in the leaf level it contains _products_
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