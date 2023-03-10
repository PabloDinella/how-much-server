(ns how_much.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [how_much.middleware :as mid]
            [how_much.ui :as ui]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire]
            [clojure.set :as set]
            [ring.middleware.cors :refer [wrap-cors]]))

(defn app [{:keys [session biff/db] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))]
    (ui/page
     {}
     nil
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]
     [:.h-6]
     (biff/form
      {:action "/community"}
      [:button.btn {:type "submit"} "New community"]))))

(defn new-community [{:keys [session] :as req}]
  (let [comm-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :community
                      :xt/id comm-id
                      :comm/title (str "Community #" (rand-int 1000))}
                     {:db/doc-type :membership
                      :mem/user (:uid session)
                      :mem/comm comm-id
                      :mem/roles #{:admin}}])
    {:status 303
     :headers {"Location" (str "/community/" comm-id)}}))

(defn community [{:keys [biff/db path-params] :as req}]
  (if-some [comm (xt/entity db (parse-uuid (:id path-params)))]
    (ui/page
     {}
     [:p "Welcome to " (:comm/title comm)])
    {:status 303
     :headers {"location" "/app"}}))

(defn delete-balance [{:keys [biff/db path-params] :as req}]
  (biff/pprint (parse-uuid (:id path-params)))
  (def result (biff/submit-tx req
                              [{:xt/id (parse-uuid (:id path-params))
                                :db/op :delete}]))

  ;; (def result (biff/q db '{:find [(pull b [:xt/id :balance/name])]
  ;;                          :where [[b :xt/id id]
  ;;                                  [b :balance/name balance-name]]}))

  (biff/pprint result)

  {:status 200
   :body result})

(defn distinct-by [f coll]
  (let [groups (group-by f coll)]
    (map #(first (groups %)) (distinct (map f coll)))))

(biff/pprint (group-by :id [{:id 1 :name "a"}
                            {:id 2 :name "b" :outraProp 2}
                            {:id 2 :name "b" :outraOutraProp 2}]))

(clojure.set/join [{:id 2, :name "b", :outraProp 2}] [{:id 2, :name "b", :outraOutraProp 2}] {:id :id})

(defn list-balance [{:keys [biff/db path-params] :as req}]
  (def result (biff/q db '{:find {:id id :name name}
                           :where [[b :balance/name name]
                                   [b :xt/id id]]}))

  ;; (def result (biff/q db '{:find [(pull b [:xt/id :balance/name])]
  ;;                          :where [[b :xt/id id]
  ;;                                  [b :balance/name balance-name]]}))

  (biff/pprint result)

  {:status 200
   :body result})

(defn list-balance-with-values [{:keys [biff/db path-params] :as req}]
  (let [resultFrom (biff/q db '{:find {:id id
                                       :name name
                                       :outgoings (sum from-amount)}
                                :where [[balance :balance/name name]
                                        [balance :xt/id id]
                                        [movement-from :xt/id from-id]
                                        [movement-from :movement/from id]
                                        [movement-from :movement/amount from-amount]]})



        resultTo (biff/q db '{:find {:id id
                                     :name name
                                     :income (sum to-amount)}
                              :where [[balance :balance/name name]
                                      [balance :xt/id id]
                                      [movement-to :xt/id to-id]
                                      [movement-to :movement/to id]
                                      [movement-to :movement/amount to-amount]]})

        resultsCombined (clojure.set/join resultFrom resultTo {:id :id})

        result (map
                (fn
                  [{id :id :as item}]

                  (biff/pprint resultsCombined)
                  (let [;; withFromAndToSums (assoc x :income (:income (first (filter #(= id (:id %)) resultTo))))
                        withBalance (assoc item :balance (- (or (:income item) 0) (or (:outgoings item) 0)))
                        ;; withBalance 1
                        ]
                    ;; (biff/pprint resultFrom)
                    ;; (biff/pprint resultTo)
                    ;; (biff/pprint (or (:income withFromAndToSums) 0))
                    withBalance))
                resultsCombined)]


    ;; (biff/pprint result)
    ;; (biff/pprint "----")
    ;; (biff/pprint (distinct-by :id (concat resultFrom resultTo)))
    ;; (biff/pprint "----")
    {:status 200
     :body result}))

(defn list-movements [{:keys [biff/db path-params] :as req}]
  (def result (biff/q db '{:find {:id   id
                                  :type type
                                  :fromId from-balance
                                  :from from-balance-name
                                  :toId to-balance
                                  :toName to-balance-name
                                  :amount amount}
                           :where [[movement     :xt/id           id]
                                   [movement     :movement/type   type]
                                   [movement     :movement/from   from-balance]
                                   [from-balance :balance/name    from-balance-name]
                                   [movement     :movement/to     to-balance]
                                   [to-balance   :balance/name    to-balance-name]
                                   [movement     :movement/amount amount]]}))
  {:status 200
   :body result})


(defn add-balance [{{name :name} :params :as req}]
  (let [balance-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :balance
                      :xt/id balance-id
                      :balance/name name}])
    {:status 200}))

(defn add-movement [{{from :from to :to} :params :as req}]
  (let [movement-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :movement
                      :xt/id movement-id
                      :movement/from (parse-uuid from)
                      :movement/to (parse-uuid to)
                      :movement/amount 10
                      :movement/type #{:expense}}])
    {:status 303
     :headers {"Location" (str "/community/" movement-id)}}))

(defn remove-movement [{{id :id} :path-params :as req}]
  (biff/pprint id)
  (biff/pprint (biff/submit-tx req
                               [{:db/op :delete
                                 :xt/id (parse-uuid id)}]))
  {:status 200})

(def features
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app"           {:get app}]
            ;; ["/balance"       {:post add-balance}]
            ["/community"     {:post new-community}]
            ["/community/:id" {:get community}]]
   :api-routes ["" {:middleware [;;mid/wrap-signed-in
                                 [wrap-cors
                                  :access-control-allow-origin #".*"
                                  :access-control-allow-methods [:get :post]]]}
                ["/balance"       {:post add-balance,
                                   :get list-balance-with-values
                                   :delete delete-balance}]
                ["/balance/:id"       {:delete delete-balance}]
                ["/balance-simple"       {:get list-balance}]
                ["/movement"       {:get list-movements,
                                    :post add-movement}]
                ["/movement/:id"       {:delete remove-movement}]]})