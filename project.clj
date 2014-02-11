(defproject cas-single-sign-out "0.1.3-SNAPSHOT"
  :description "Ring middleware for CAS single sign out"
  :url "https://github.com/solita/cas-single-sign-out"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/data.zip "0.1.1"]
                 [ring/ring-core "1.2.1"]
                 [robert/hooke "1.3.0"]
                 [clj-cas-client "0.0.6" :exclusions [ring]]]
  :profiles {:dev {:dependencies [[ring-mock "0.1.5"]]}})
