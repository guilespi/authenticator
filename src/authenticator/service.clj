(ns authenticator.service
  (:require [taoensso.carmine :as car])
  (:use components.lifecycle.protocol))


(defmacro exec
  [service & body]
  `(car/wcar (handler ~service) ~@body))


(defn set-and-expire!
  [redis redis-ns id value ttl]
  (exec redis
        (car/set (str redis-ns ":" id) value)
        (car/expire (str redis-ns ":" id) ttl)))


(defn get-key
  [redis redis-ns id]
  (exec redis
        (car/get (str redis-ns ":" id))))

(defn del!
  [redis redis-ns id]
  (exec redis (car/del (str redis-ns ":" id))))

(def auth-ns "Authenticator")

(defprotocol Authenticator
  (generate-token [this id] "Creates a temporary token given an identifier")
  (resolve-token [this token] "Given a token returns related identifier")
  (read-token [this token] "Returns token identifier without removal")
  (clear-token [this token] "Deletes token reference"))


(defrecord AuthComponent
    [state settings]

  Lifecycle
  (stop [this system]
    )

  (start [this system]
    (swap! state
           assoc :redis
           {:pool {} :spec {:host (:host settings)
                            :port (:port settings)}}))

  Service
  (handler [_]
    (:redis @state))

  Authenticator
  (generate-token [this id]
    (let [new-token (str (java.util.UUID/randomUUID))]
      (set-and-expire! this
                       auth-ns
                       new-token
                       (str id)
                       (or (:expire-timeout settings) 30))
      new-token))

  (resolve-token [this token]
    (when-let [id (get-key this auth-ns token)]
      (del! this auth-ns token)
      id))

  (read-token [this token]
    (get-key this auth-ns token))

  (clear-token [this token]
    (del! this auth-ns token))

)

(defn make
  "Creates an authenticator component"
  [settings]
  (->AuthComponent (atom {}) settings))
