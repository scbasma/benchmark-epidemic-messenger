(defproject benchmark-epidemic-messenger "0.1.0-SNAPSHOT"
  :main benchmark-epidemic-messenger.core
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.443"]
                 [org.onyxplatform/onyx "0.10.0-epidemics"]
                 [org.onyxplatform/lib-onyx "0.10.0.0"]
                 [aysylu/loom "1.0.0"]]

  :profiles {:uberjar {:aot [lib-onyx.media-driver
                           benchmark-epidemic-messenger.core]
                     :uberjar-name "benchmark-epidemic.jar"
                     :global-vars {*assert* false}}})
