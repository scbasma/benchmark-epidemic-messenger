(ns benchmark-epidemic-messenger.core
  (require [onyx.messaging.aeron.epidemic-messenger :refer [build-aeron-epidemic-messenger]]
           [clojure.core.async :refer [chan]]))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn -main [& args]
  (let [peer-config {:onyx.messaging.aeron/embedded-driver? false
                     :onyx.messaging.aeron/embedded-media-driver-threading :shared
                     :onyx.messaging/peer-port 40199
                     :onyx.messaging/bind-addr "127.0.0.1"
                     :onyx.peer/subscriber-liveness-timeout-ms 500
                     :onyx.peer/publisher-liveness-timeout-ms 500
                     :onyx.messaging/impl :aeron}
        epidemic-channels (repeat 100 (chan 100))
        messenger-list (doall (map #(build-aeron-epidemic-messenger peer-config nil nil %) epidemic-channels))]

    (println "helloo")))
