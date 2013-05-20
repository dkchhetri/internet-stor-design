(defproject iStor "0.0.1"
  :description "file storage"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [http-kit "2.1.1"]
                 [ring "1.1.8"]
                 [compojure "1.1.5"]
                 [clout "1.1.0"]
                 [ring-mock "0.1.4"]]
  :url "http://noname.org/"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                  [ring "1.1.8"]
                                  [compojure "1.1.5"]
                                  [clout "1.1.0"]
                                  [ring-mock "0.1.4"]
                                  [http.async.client "0.5.2"]]}})
