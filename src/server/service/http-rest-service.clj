(def rest-service-port 8080)

(defn http-rest-handler [ring-request]
  (with-channel ring-request channel
    (if (websocket? channel)
      (on-receive channel (fn [data]
                            (send! channel data)))
      (send! channel {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body    "Long polling?"}))))

;; POST /pumkin/v1/<stor name>/sync-txn
(defn start-txn-handler [req]
  )

;; DELETE /pumkin/v1/<stor name>/abort-txn/<transaction-id>
(defn abort-txn-handler [req]
  )

;; POST /pumkin/v1/<stor name>/commit-txn/<transaction-id>
(defn commit-txn-handler [req]
  )

;; GET /pumkin/v1/<stor name>/txn-info/<transaction-id>/parent-file-index
(defn txn-info-pfile-idx-handler [req]
  )

(defroutes all-routes
  (GET "/" [] "<p> Pumkin Home </p>")
  (POST "/pumkin/v1/:container/sync-txn" [] start-txn-handler)
  (DELETE "/pumkin/v1/:container/abort-txn/:txn-id" [] abort-txn-handler)
  (POST "/pumkin/v1/:container/commit-txn/:txn-id" [] commit-txn-handler)
  (GET "/pumkin/v1/:container/txn-info/:txn-id/parent-file-index" [] txn-info-pfile-idx-handler)
  (ANY "*" "<p>Page not found. </p>"))

(run-server (site #'all-routes) {:port 8080})

(run-server http-rest-handler {:port rest-service-port})
