(ns clojars.web.search
  (:require [clojars.web.common :refer [html-doc jar-link jar-fork?
                                        collection-fork-notice user-link
                                        format-date page-nav]]
            [clojars.search :as search]
            [cheshire.core :as json]))

(defn- jar->json [jar]
  (let [m {:jar_name (:artifact-id jar)
           :group_name (:group-id jar)
           :version (:version jar)
           :description (:description jar)}
        created (:at jar)]
    (if created
      (assoc m :created created)
      m)))

(defn json-search [search query]
  (let [response {:status 200
                  :headers {"Content-Type" "application/json; charset=UTF-8"}}]
    (try
      (assoc response
        :body (let [results (search/search search query 1)]
                (json/generate-string {:count (count results)
                                       :results (map jar->json results)})))
      (catch Exception _
        (assoc response
          :status 400
          :body (json/generate-string
                  {:error (format "Invalid search syntax for query `%s`" query)}))))))

(defn html-search [search account query page]
  (html-doc account (str query " - search")
    [:div.light-article.row
     [:h1 "Search for '" query "'"]
     (try
       (let [results (search/search search query page)
             {:keys [total-hits results-per-page offset]} (meta results)]
         (if (empty? results)
           [:p "No results."]
           [:div
            [:p (format "Total results: %s, showing %s - %s"
                  total-hits (inc offset) (+ offset (count results)))]
            (if (some jar-fork? results)
              collection-fork-notice)
            [:ul.row
             (for [jar results]
               [:li.search-results.col-md-4.col-lg-3.col-sm-6.col-xs-12
                [:div.result
                 (jar-link {:jar_name (:artifact-id jar)
                            :group_name (:group-id jar)}) " " (:version jar)
                 [:br]
                 (when (seq (:description jar))
                   [:span.desc (:description jar)
                    [:br]])
                 [:span.details (if-let [created (:created jar)]
                                  [:td (format-date created)])]]])]
            (page-nav (Integer. page)
              (int (Math/ceil (/ total-hits results-per-page)))
              :base-path (str "/search?q=" query "&page="))]))
       (catch Exception _
         [:p "Could not search; please check your query syntax."]))]))

(defn search [search account params]
  (let [q (params :q)
        page (or (params :page) 1)]
    (if (= (params :format) "json")
      (json-search search q)
      (html-search search account q page))))
