(defproject op0010 "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [crate "0.2.5"]
                 [domina "1.0.3"]]
  :plugins [[lein-cljsbuild "1.0.3"]]
  :cljsbuild {:builds {:dev {:id "dev"
                             :source-paths ["src/cljs" "src/dev"]
                             :compiler {:output-to "resources/public/cljs.js"
                                        :foreign-libs [
                                                       ;{:file "src/js/howler_.js", :provides ["Howl" "Howler"]}
                                                       ]
                                        :language-in :ecmascript5
                                        :language-out :ecmascript5
                                        :optimizations :whitespace
                                        :pretty-print true}
                              :jar true}
                       :prod {:id "prod"
                              :source-paths ["src/cljs" "src/prod"]
                              :compiler {:output-to "resources/public/cljs.js"
                                         :foreign-libs [
                                                        ;{:file "src/js/howler_.js", :provides ["Howl" "Howler"]}
                                                        ]
                                         :language-in :ecmascript5
                                         :language-out :ecmascript5
                                         :optimizations :simple ;:advanced
                                         :pretty-print false}
                              :jar true}
                       }}
  )

