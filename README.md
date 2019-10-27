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

# Motivation

If you are using a document oriented database, you probably end up having fairly large object trees. In order to use these trees you end up having a lot of code that looks like
```clojure
(->> db
     (:customers)
     (vals)
     (mapcat #(:products %)))
```
While it is quite ok to write, and it works, it hides the purpose of the code behind several functions. The reader of your code should see that you want all products from all customers, like
```clojure
(select [:customers ** :products **] db)
```
where the meaning is clear. And if you only want to take one customer (with key 724), you dont have to change the feeling of the code at all
```clojure
(select [:customers 724 :products **] db)
```
Or if you want to compute the sum of all the items for one customer:
```clojure
(select-1 [:customers 724 :products ** :price =>sum] db)
```


# Usage

The select part of the API has two functions:
```clojure
(select [...] data)
```
and 
```clojure
(select-1 [...] data)
```
The former one returns a sequence and the latter takes the first element of the sequence and return that. `select-1` is handy when combined with reducing selectors, like
```clojure
(select-1 [:customers ** :products ** :price =>sum] db)
```
Reducing selectors have a prefix `=>` and they return just one element, so `select-1` works nicely with them.

If `select` or `select-1` is called without the data to work on, they return [transducers](https://clojure.org/reference/transducers) just like core library functions `map, take, etc.`. A transducer is a composable transformation for data.

## The first argument to select

The first argument to select is actually a vector of transducers. But as you know, a keyword or a number is not a transducer. Keywords, numbers and strings are automatically converted into transducers. (More on this a bit later.)

So, because select can return a transducer and it takes a vector of transducers as a parameter, *you can compose selects*:
```clojure
(let [customers (select [:customers **])
      products (select [:products **])]
  (select [customers products] db))
```

# Examples of all the things that you can do with select

