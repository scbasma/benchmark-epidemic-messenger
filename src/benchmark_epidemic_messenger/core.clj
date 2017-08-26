(ns benchmark-epidemic-messenger.core
  (:gen-class)
  (require [onyx.messaging.aeron.epidemic-messenger :refer [build-aeron-epidemic-messenger pick-streams]]
           [onyx.messaging.protocols.epidemic-messenger :as epm]
           [loom.graph :refer [weighted-digraph edges nodes]]
           [loom.alg :refer [all-pairs-shortest-paths]]
           [loom.io :refer [view]]
           [clojure.core.matrix.stats :refer [variance]]
           [clojure.core.async :refer [chan <!! close!]]
           [onyx.messaging.aeron.embedded-media-driver :as em]
           [com.stuartsierra.component :as component]))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(def messages [{:message-id 0 :log-info "entry-0" :TTL 4 :transmitter-list []}])

; (let [messenger-2-entries (<!! (into [] epidemic-ch-2))]
(defn drain-channel [channel]
  (let [entries (<!! (clojure.core.async/into [] channel))]
    (println "Entries: " entries)
    entries))

(defn graph [g]
  (weighted-digraph g ))


(defn start-benchmark [messenger]
  (epm/update-log-entries messenger (assoc (first messages) :messenger-id (epm/get-messenger-id messenger))))

(defn get-info [messenger]
  (epm/info messenger))


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

(defn -main [& args]
  (println "Args: " (first args))
  (let [peer-config {:onyx.messaging.aeron/embedded-driver? true
                     :onyx.messaging.aeron/embedded-media-driver-threading :shared
                     :onyx.messaging/peer-port 40199
                     :onyx.messaging/bind-addr "localhost"
                     :onyx.peer/subscriber-liveness-timeout-ms 500
                     :onyx.peer/publisher-liveness-timeout-ms 500
                     :onyx.messaging/impl :aeron
                     :peer-number 55}

    ;    media-driver (component/start (em/->EmbeddedMediaDriver peer-config))
    ;    epidemic-channels (repeatedly (:peer-number peer-config) #(chan 100))
    ;    _ (println "Epidemic channels: " epidemic-channels)
    ;    messenger-list (doall (map #(build-aeron-epidemic-messenger peer-config nil nil %) epidemic-channels))
    ;    write-info (partial write-string "55-peers.txt")
        ;_ (println "Messenger-list: " messenger-list)
        number-peers (Integer/parseInt (first args))
        number-iterations (Integer/parseInt (second args))

        ;peers (generate-peers number-peers)
        ;average-degrees (repeatedly number-iterations #(average-degree-distribution (generate-peers number-peers)))

        ;avg-of-avg-degrees (average-of-average-degree-distribution average-degrees)

       ; graph (apply weighted-digraph (create-graph (set peers)))
        ;average-shortest-paths (repeatedly number-peers #(let [digraph (create-digraph number-peers)]
                                                   ;(average-shortest-path (all-pairs-shortest-paths digraph)
                                                   ;                       (count (edges digraph)))))
        ;average-shortest-path (float (/ (reduce #(+ %1 %2) 0 average-shortest-paths) (count average-shortest-paths)))
        calcs (do-calculations number-peers number-iterations)
        ]
    ;(println "Average degree distribution: " average-degrees)
    ;(println "Average average in degree: " avg-of-avg-degrees)
    ;(println "PEERS: " peers)
    (write-calcs number-peers number-iterations calcs)
    ;(println "Connectivity: " (connectivity? peers))
    ;(println "GRAPH: " graph)
    ;(view graph)
    ;(println "average-degrees: " average-degrees)
    ;(println "Average-of-average-degrees: " avg-of-avg-degrees)
    ;(println "average-shortest-paths: : " average-shortest-paths)
    ;(println "Average-shortest-path: " average-shortest-path)
    ;(println "GRAPH OF PEERS: " (create-graph (set peers)))
    ;(println "do-calculations: " (do-calculations number-peers number-iterations))
    ;(doall (map (partial write-string "degree-distribution.txt") degree-distribution))


    ;(try
      ;(start-benchmark (first messenger-list))
    ;  (doall (map #(write-info (get-info %)) messenger-list))
    ;  (Thread/sleep 1000)
    ;  (doall (map #(close! %) epidemic-channels))
    ;  (doall (map #(drain-channel %) epidemic-channels))
    ;  (Thread/sleep 1000)
    ;  (finally
    ;    (doall (map #(epm/stop %) messenger-list))
    ;    (component/stop media-driver)))
    ))


