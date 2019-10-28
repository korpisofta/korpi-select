# korpi-select
[![Build Status](https://travis-ci.com/korpisofta/korpi-select.svg?branch=master)](https://travis-ci.com/korpisofta/korpi-select)
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

# More motivation

If you are using a document oriented database, you probably end up having fairly large object trees. In order to use these trees you write code that looks like
```clojure
(->> db
     (:customers)
     (vals)
     (mapcat #(:products %))
     (keep :price)
     (reduce + 0))
```
Although this kind of code works, it is full of noise. The reader of your code should see what you mean:
```clojure
(select-1 [:customers ** :products ** :price =>sum] db)
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
The former one returns a sequence and the latter takes the first element of the sequence and returns that. `select-1` is handy when combined with reducing selectors, like
```clojure
(select-1 [:customers ** :products ** :price =>sum] db)
```
Reducing selectors have a prefix `=>` and they return just one element.

# Select is a transducer

If `select` or `select-1` are called without the data to work on, they return [transducers](https://clojure.org/reference/transducers) just like core library functions `map, take, etc.`. The first argument to select is a vector of transducers. So basically `select` is function that composes transducers. 

In addition top composing transducers, select transforms things into transducers. In the previous examples, the vector argument had keywords. Keywords, numbers and strings are automatically converted into transducers. There are two kinds of automatic conversions:
1. functions are converted using `keep`
1. everything else is converted by assuming that it is a key in an associative collection

So, because select can return a transducer and it takes a vector of transducers as a parameter, *you can compose selects*:
```clojure
(let [customers (select [:customers **])
      products (select [:products **])]
  (select [customers products] db))
```

# Examples

## Select using number or string as a map key
```clojure
(select [:customers 724] db)
```
## Select all items in a sequence or all values in a map
```clojure
(select [:customers **] db)
```
## Select recursively from all nested maps or sequences
This selection method works like `//` -selector in XPath.
```clojure
(select [-all- :price] db)
```
## Reducing values
Reducing transducers have a naming convention: they start with `=>`. There are five predefined transducers: `=>count` `=>sum` `=>vec` `=>first` `=>last`
```clojure
(select-1 [-all- :price =>sum] db)
```
## Using other functions
All non-transducer functions are transformed into transducers using keep, so
```clojure
(select [:customers ** #(select-keys % [:name])] db)
```
is equivalent to
```clojure
(select [:customers ** (keep #(select-keys % [:name]))] db)
```
## Extracting keys from maps




# License

© Korpisofta Oy

Apache License Version 2.0




