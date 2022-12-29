(ns how_much.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :uuid
   :user    [:map {:closed true}
             [:xt/id          :user/id]
             [:user/email     :string]
             [:user/joined-at inst?]]

   :comm/id   :uuid
   :community [:map {:closed true}
               [:xt/id      :comm/id]
               [:comm/title :string]]

   :mem/id     :uuid
   :membership [:map {:closed true}
                [:xt/id     :mem/id]
                [:mem/user  :user/id]
                [:mem/comm  :comm/id]
                [:mem/roles [:set [:enum :admin]]]]

   :chan/id :uuid
   :channel [:map {:closed true}
             [:xt/id      :chan/id]
             [:chan/title :string]
             [:chan/comm  :comm/id]]

   :msg/id  :uuid
   :message [:map {:closed true}
             [:xt/id          :msg/id]
             [:msg/mem        :mem/id]
             [:msg/text       :string]
             [:msg/channel    :chan/id]
             [:msg/created-at inst?]]

   :balance/id :uuid
   :balance [:map {:closed true}
             [:xt/id :balance/id]
             [:balance/name :string]]

   :movement/id :uuid
   :movement [:map {:closed true}
              [:xt/id :movement/id]
              [:movement/type [:set [:enum :expense :income]]]
              [:movement/from :balance/id]
              [:movement/to :balance/id]
              [:movement/amount :int]]})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
