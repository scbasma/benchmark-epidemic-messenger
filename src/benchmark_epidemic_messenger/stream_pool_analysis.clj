(ns benchmark-epidemic-messenger.stream-pool-analysis
  (require
    [onyx.messaging.aeron.epidemic-messenger :refer [pick-streams]]
    [loom.graph :refer [weighted-digraph edges nodes]]
    [loom.alg :refer [all-pairs-shortest-paths]]
    [loom.io :refer [view]]
    [clojure.core.matrix.stats :refer [variance]]))

(defn create-graph [peer-set]
  (reduce #(conj %1 [ (keyword (str (:publisher-stream %2))) (keyword (str (:subscriber-stream %2))) 1]) [] peer-set))


(defn shortest-path-length [peer all-peers])

(defn degree-distribution [all-peers peer]
  (let [pub-stream (:publisher-stream peer)
        sub-stream (:subscriber-stream peer)
        in-degree (reduce #(+ %1 (if (= (:publisher-stream %2) sub-stream) 1 0)) 0 all-peers)
        out-degree (reduce #(+ %1 (if (= (:subscriber-stream %2) pub-stream) 1 0)) 0 all-peers)
        ]
    {:in-degree (float (/ in-degree (count all-peers))) :out-degree (float (/ out-degree (count all-peers)))}
    ))

(defn average-degree-distribution [peers]
  (let [degree-d-fn (partial degree-distribution peers)
        degree-distribution (map degree-d-fn peers)]
    {:avg-in-degree (float (/ (reduce #(+ %1 (:in-degree %2)) 0 degree-distribution ) (count degree-distribution)))
     :avg-out-degree (float (/ (reduce #(+ %1 (:out-degree %2)) 0 degree-distribution) (count degree-distribution)))
     :variance (variance (map :in-degree degree-distribution))}))

(defn average-of-average-degree-distribution [average-degrees]
  {:avg-avg-in-degree (float (/ (reduce #(+ %1 (:avg-in-degree %2)) 0 average-degrees) (count average-degrees)))
   :avg-avg-out-degree (float (/ (reduce #(+ %1 (:avg-out-degree %2)) 0 average-degrees) (count average-degrees)))
   :avg-variance (float (/ (reduce #(+ %1 (:variance %2)) 0 average-degrees) (count average-degrees)))})

(defn pick-peers [number-peers]
  (repeatedly number-peers #(let [streams (pick-streams number-peers)]
                              {:publisher-stream (first streams) :subscriber-stream (second streams)})))

(defn flatten-map
  "Flattens the keys of a nested into a map of depth one but
   with the keys turned into vectors (the paths into the original
   nested map)."
  [s]
  (let [combine-map (fn [k s] (for [[x y] s] {[k x] y}))]
    (loop [result {}, coll s]
      (if (empty? coll)
        result
        (let [[i j] (first coll)]
          (recur (into result (combine-map i j)) (rest coll)))))))

(defn map-leaves [f x]
  (cond
    (map? x) (persistent!
               (reduce-kv (fn [out k v]
                            (assoc! out k (map-leaves f v)))
                          (transient {})
                          x))
    (set? x) (into #{} (map #(map-leaves f %)) x)
    (sequential? x) (into [] (map #(map-leaves f %)) x)
    :else (f x)))


(defn flatten-tree [x]
  (filter #(not (map? %)) (tree-seq map? vals x)))

(defn average-shortest-path [graph count]
  (float (/ (reduce + (flatten-tree graph)) count)))


(defn connectivity? [peers]
  (let [publisher-stream-set (reduce #(conj %1 (:publisher-stream %2)) #{} peers)
        subscriber-stream-set (reduce #(conj %1 (:subscriber-stream %2)) #{} peers)]
    (= publisher-stream-set subscriber-stream-set)))



(defn generate-peers [n-peers]
  (repeatedly n-peers #(let [streams (pick-streams n-peers)]
                         {:publisher-stream (first streams) :subscriber-stream (second streams)})))

(defn create-digraph [number-peers]
  (apply weighted-digraph (create-graph (set (generate-peers number-peers)))))

(defn do-calculations [number-peers number-iterations]
  (let [average-degrees (repeatedly number-iterations #(average-degree-distribution (generate-peers number-peers)))
        avg-of-avg-degrees (average-of-average-degree-distribution average-degrees)
        ; graph (apply weighted-digraph (create-graph (set peers)))
        average-shortest-paths (repeatedly number-peers #(let [digraph (create-digraph number-peers)]
                                                           (average-shortest-path (all-pairs-shortest-paths digraph)
                                                                                  (count (edges digraph)))))
        average-shortest-path (float (/ (reduce #(+ %1 %2) 0 average-shortest-paths) (count average-shortest-paths)))
        connected (every? true? (repeatedly number-peers #(connectivity? (generate-peers number-peers))))]
    {:avg-degrees avg-of-avg-degrees
     :average-shortest-path average-shortest-path
     :all-connected connected}))

(defn write-string [file-name s]
  (spit file-name (str s "\n") :append true)
  )

(defn write-calcs [number-peers number-iterations calcs]
  (println calcs)
  (let [file-name (str number-peers "-peers-" number-iterations "-iterations-.txt")
        write-string-f (partial write-string file-name)]
    (write-string-f (str "Number of peers: " number-peers ""))
    (write-string-f (str "Number of iterations: " number-iterations))
    (write-string-f (str "Average degree distributions: \n"
                         "\t average-out-degree: " (:avg-avg-out-degree (:avg-degrees calcs)) "\n"
                         "\t average-in-degree: " (:avg-avg-in-degree (:avg-degrees calcs))
                         "\t average variance: " (:avg-variance (:avg-degrees calcs))))
    (write-string-f (str "Average shortest path: " (:average-shortest-path calcs)))
    (write-string-f (str "All connected: " (:all-connected calcs)))))

