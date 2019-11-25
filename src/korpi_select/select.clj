(ns korpi-select.select)

(declare ** values)

;; Protocol for transforming objects into transducers

(defprotocol Transducerifier
  (as-transducer [item]))

(defmacro mark-as-transducer [xform]
  `(extend-type (class ~xform)
     Transducerifier
     (as-transducer [item#] item#)))

(mark-as-transducer cat)
(mark-as-transducer (dedupe))
(mark-as-transducer (distinct))
(mark-as-transducer (drop 1))
(mark-as-transducer (drop-while identity))
(mark-as-transducer (filter identity))
(mark-as-transducer (halt-when identity))
(mark-as-transducer (interpose ""))
(mark-as-transducer (keep identity))
(mark-as-transducer (keep-indexed identity))
(mark-as-transducer (map identity))
(mark-as-transducer (map-indexed identity))
(mark-as-transducer (mapcat identity)) ;; actually comp
(mark-as-transducer (partition-all 1))
(mark-as-transducer (partition-by identity))
(mark-as-transducer (random-sample 1.0))
(mark-as-transducer (remove identity))
(mark-as-transducer (replace {}))
(mark-as-transducer (take 1))
(mark-as-transducer (take-nth 1))
(mark-as-transducer (take-while identity))

(extend-type clojure.lang.IFn
  Transducerifier
  (as-transducer [item] (keep item)))

(extend-type java.lang.Object
  Transducerifier
  (as-transducer [item] (values item))) ;; By default it must be a map key

;; core methods

(defn select
  ([keys]
   (apply comp (map as-transducer keys)))
  ([keys item]
   (sequence (select keys) [item])))

(defn select-1 [keys coll]
  (first (select (conj keys (take 1)) coll)))

(def **
  (comp
    (mapcat #(when (coll? %) (if (map? %) (vals %) %)))
    (keep identity)))

(defn values [& keys] (comp (map #(select-keys % keys)) **))

(defn walk-map [m]
  (cond
    (map? m) (lazy-seq (cons m (mapcat walk-map (vals m))))
    (coll? m) (lazy-seq (cons m (mapcat walk-map m)))
    :else [m]))

(def -all- (comp (mapcat walk-map) (keep identity)))

;; reducers

(defn reducer [f initf]
  (fn [rf]
    (let [value (volatile! (initf))]
      (fn
        ([] (rf))
        ([result] (rf (unreduced (rf result @value))))
        ([result input] (vswap! value f input))))))

(def =>sum (reducer + (constantly 0)))
(def =>count (comp (map (constantly 1)) =>sum))
(def =>vec (comp (reducer conj! #(transient [])) (keep persistent!)))
(def =>first (take 1))
(def =>last (reducer (fn [_ x] x) (constantly nil)))

(mark-as-transducer =>sum)

;; pull and push

(defn pull
  ([key xform]
   (map #(assoc % key (select-1 xform %))))
  ([key xform & key-forms]
   (comp
     (pull key xform)
     (apply pull key-forms))))

(defn push [& ks]
  (let [keys (butlast ks)
        xforms (last ks)]
    (mapcat (fn [item]
              (->> (select xforms item)
                   (map #(merge % (select-keys item keys))))))))

(defn push-key
  ([key]
   (push-key key []))
  ([key xforms]
   (letfn [(key-into-map [value item]
             (map #(assoc % key value) (select xforms item)))]
     (mapcat #(if (map? %)
                (mapcat (partial apply key-into-map) %)
                (apply concat (map-indexed key-into-map %)))))))

(defn rename
  ([src dst]
   (comp
     (map #(assoc % dst (get % src)))
     (map #(dissoc % src))))
  ([src dst & kvs]
   (comp
     (rename src dst)
     (apply rename kvs))))

;; in-depth filters

(defn exists [xforms]
  (filter #(select-1 (conj xforms (filter identity)) %)))

(defn all [xforms]
  (filter #(every? identity (select xforms %))))
