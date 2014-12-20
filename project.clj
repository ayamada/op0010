(defproject op0010 "0.1.5-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2411"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :plugins [[lein-cljsbuild "1.0.3"]]
  :cljsbuild {:builds {:dev {:id "dev"
                             :source-paths ["src/cljs"]
                             :compiler {:output-to "resources/public/cljs.js"
                                        :language-in :ecmascript5
                                        :language-out :ecmascript5
                                        :optimizations :whitespace #_:simple
                                        :pretty-print true}
                              :jar true}}})

