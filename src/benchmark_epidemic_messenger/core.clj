(ns benchmark-epidemic-messenger.core
  (:gen-class)
  (require [onyx.messaging.aeron.epidemic-messenger :refer [build-aeron-epidemic-messenger pick-streams]]
           [onyx.messaging.protocols.epidemic-messenger :as epm]
           [onyx.messaging.aeron.embedded-media-driver :as em]
           [benchmark-epidemic-messenger.stream-pool-analysis :refer [do-calculations write-string]]
           [com.stuartsierra.component :as component]
           [clojure.core.async :refer [chan <!! close!]]))


(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(def messages [{:message-id 0 :log-info "entry-0" :TTL 2 :transmitter-list []}])

; (let [messenger-2-entries (<!! (into [] epidemic-ch-2))]
(defn drain-channel [channel]
  (let [entries (<!! (clojure.core.async/into [] channel))]
    ;(println "Entries: " entries)
    entries))


(defn start-benchmark [messenger]
  (epm/update-log-entries messenger (assoc (first messages) :messenger-id (epm/get-messenger-id messenger))))

(defn get-info [messenger]
  (epm/info messenger))

(defn dissemination-coverage [messenger-list messenger-entries]
  (float (/ (- (count messenger-list) (count (filter #(empty? (:entries %)) messenger-entries))) (count messenger-list))))

(defn messages-per-sub-stream [messenger-entries]
  (let [per-stream (group-by #(keyword (str (:subscriber-stream (:info %)))) messenger-entries)
        redundant-per-stream (map
                               #(reduce (fn [x y] (+ x (:redundant-message-count (:info y)))) 0 (% per-stream))
                            (keys per-stream))

        total-per-stream (map
                               #(reduce (fn [x y] (+ x (:total-message-count (:info y)))) 0 (% per-stream))
                               (keys per-stream))
        ]
    {:redundant-per-stream redundant-per-stream :total-per-stream total-per-stream})
  )

(defn redundant-vs-total [redundant-tally total-tally]
  (let [sum-redundant (reduce + redundant-tally)
        sum-total (reduce + total-tally)]
    (float (/ sum-redundant sum-total))))

(defn get-info-and-entries [messenger]
  {:info (epm/info messenger) :entries (epm/get-all-log-events messenger)})


(defn execute-analysis [messenger-list]
  (let [entries (doall (map #(get-info-and-entries %) messenger-list))
        dissemination-coverage (dissemination-coverage messenger-list entries)
        group-by-sub (messages-per-sub-stream entries)
        redundant-vs-total (redundant-vs-total (:redundant-per-stream group-by-sub) (:total-per-stream group-by-sub))]
    {:dissemination-coverage dissemination-coverage
     :group-by-sub group-by-sub
     :redundant-vs-total redundant-vs-total}))

(defn write-stats [number-peers number-iterations stat-list avgs ttl]
  (let [file-name (str number-peers "dissemination-peers-" number-iterations "-iterations-.txt")
        write-string-f (partial write-string file-name)]
    (write-string-f (str "Number of peers: " number-peers ""))
    (write-string-f (str "Number of iterations: " number-iterations))
    (write-string-f (str "TTL: " ttl))
    (write-string-f (str "stat-list: " stat-list))
    (write-string-f (str "Averages: " avgs))))


(defn execute-send-messages [peer-config]
  (let [epidemic-channels (repeatedly (:peer-number peer-config) (fn [] (chan 100)))
        messenger-list (doall
                         (map (fn [c] (build-aeron-epidemic-messenger peer-config nil nil c)) epidemic-channels))]
    (try
      (start-benchmark (first messenger-list))
      (Thread/sleep 1000)
      (let [results (execute-analysis messenger-list)]
        results)
      (finally
        (doall (map #(epm/stop %) messenger-list)))

    )))


(defn execute-dissemination-test [peer-config iterations]
  (let [media-driver (component/start (em/->EmbeddedMediaDriver peer-config))]
    (try
      (let [stat-list (doall (repeatedly iterations #(execute-send-messages peer-config)))
            _ (println " STAT LIST: !!!!!" stat-list)
            avg-dissemination-coverage (float (/ (reduce #(+ %1 (:dissemination-coverage %2)) 0 stat-list) (count stat-list)))
            avg-redundant-vs-total (float (/ (reduce #(+ %1 (:redundant-vs-total %2)) 0 stat-list) (count stat-list)))
            avgs {:avg-dissemination-coverage avg-dissemination-coverage :avg-redundant-vs-total avg-redundant-vs-total}]
        (write-stats (:peer-number peer-config) iterations stat-list avgs (:TTL peer-config))
        avgs)
      (finally
        (component/stop media-driver)))))


(defn -main [& args]
  (println "Args: " (first args))
  (let [peer-config {:onyx.messaging.aeron/embedded-driver? true
                     :onyx.messaging.aeron/embedded-media-driver-threading :shared
                     :onyx.messaging/peer-port 40199
                     :onyx.messaging/bind-addr "localhost"
                     :onyx.peer/subscriber-liveness-timeout-ms 500
                     :onyx.peer/publisher-liveness-timeout-ms 500
                     :onyx.messaging/impl :aeron
                     :peer-number 55
                     :TTL 2}

        peer-number (:peer-number peer-config)
        ;_ (println "Epidemic channels: " epidemic-channels)
        ;_ (println "Messenger-list: " messenger-list)
        ;number-peers (Integer/parseInt (first args))
        ;number-iterations (Integer/parseInt (second args))

        ;calcs (do-calculations number-peers number-iterations)
        ]
    ;(write-calcs number-peers number-iterations calcs)
    ;(view graph)

    (println "averages: " (execute-dissemination-test peer-config 20))
    ;(try
    ;  (start-benchmark (first messenger-list))
      ;(doall (map #(write-info (get-info %)) messenger-list))
    ;  (Thread/sleep 1000)
    ;  (doall (map #(close! %) epidemic-channels))
    ;  (let [entries (doall (map #(get-info-and-entries %) messenger-list))
    ;        dissemination-coverage (dissemination-coverage messenger-list entries)
    ;        group-by-sub (messages-per-sub-stream entries)
    ;        redundant-vs-total (redundant-vs-total (:redundant-per-stream group-by-sub) (:total-per-stream group-by-sub))]
    ;    (println "dissemination coverage: " dissemination-coverage)
    ;    (println "group by sub: " group-by-sub)
    ;    (println "redundant vs total: " redundant-vs-total))
      ;(println "Messenger list: " (doall (map #(get-info-and-entries %) messenger-list)))
    ;  (Thread/sleep 1000)
    ;  (finally
    ;    (doall (map #(epm/stop %) messenger-list))
    ;    (component/stop media-driver)))
    ))


