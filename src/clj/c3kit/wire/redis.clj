(ns c3kit.wire.redis
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.wire.message-queue :as mq]
            [taoensso.carmine :as car]
            [taoensso.carmine.message-queue :as car-mq]))

(deftype RedisMessageQueue [workers conn-opts]
  mq/MessageQueue
  (-enqueue [_this messages]
    (car/wcar conn-opts
              (ccc/for-all [{:keys [qname message]} messages]
                (car-mq/enqueue qname message))))
  (-on-message [_this qname handler]
    (let [worker (car-mq/worker conn-opts qname {:handler handler})]
      (swap! workers conj worker)))
  (-clear [_this] (car-mq/queues-clear-all!!! conn-opts))
  (-stop [_this]
    (doseq [worker @workers]
      (worker :stop)
      (swap! workers disj worker)))
  (-queue-messages [_this qname]
    (for [[_mid message] (car-mq/queue-content conn-opts qname)]
      (assoc message :qname qname)))
  (-all-messages [this]
    (->> (car-mq/queue-names conn-opts)
         (mapcat (partial mq/-queue-messages this)))))

(defmethod mq/create :redis [{:keys [conn-opts]}]
  (RedisMessageQueue. (atom #{}) conn-opts))
