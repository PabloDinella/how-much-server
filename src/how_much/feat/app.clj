(ns how_much.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [how_much.middleware :as mid]
            [how_much.ui :as ui]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire]))

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

(defn list-balance [{:keys [biff/db path-params] :as req}]
  (def result (biff/q db '{:find {:name name
                                  :movementsOut (distinct {:id movement-from-id :amount movement-from-amount})
                                  :amountOut movement-from-amount
                                  :movementsIn (distinct {:id movement-to-id :amount movement-to-amount})
                                  :amountIn movement-to-amount}
                           :where [[b :balance/name name]
                                   [b :xt/id id]
                                   [movement-from :movement/from b]
                                   [movement-from :xt/id movement-from-id]
                                   [movement-from :movement/amount movement-from-amount]
                                   [movement-to :movement/to b]
                                   [movement-to :xt/id movement-to-id]
                                   [movement-to :movement/amount movement-to-amount]]}))

  (biff/pprint result)

  {:status 200
   :body result})

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

(def features
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app"           {:get app}]
            ;; ["/balance"       {:post add-balance}]
            ["/community"     {:post new-community}]
            ["/community/:id" {:get community}]]
   :api-routes ["" {:middleware [mid/wrap-signed-in]}
                ["/balance"       {:post add-balance,
                                   :get list-balance}]
                ["/movement"       {:post add-movement,
                                    :get list-movements}]]})