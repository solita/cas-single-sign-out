(ns cas-single-sign-out.middleware-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.memory :refer [memory-store]]
            [cas-single-sign-out.middleware :refer :all]))

(defn single-sign-out-request [ticket]
  (-> (request :post "/")
    (content-type "application/x-www-form-urlencoded")
    ;; Captured from an actual CAS single sign out request. We'll just fill in
    ;; the given ticket.
    (body (str "logoutRequest=%3Csamlp%3ALogoutRequest+xmlns%3Asamlp%3D%22urn%3"
               "Aoasis%3Anames%3Atc%3ASAML%3A2.0%3Aprotocol%22+ID%3D%22LR-7-UQQ"
               "euikIQlvANwtMBwODk2lfLSj2ektwxWX%22+Version%3D%222.0%22+IssueIn"
               "stant%3D%222014-01-20T05%3A48%3A11Z%22%3E%3Csaml%3ANameID+xmlns"
               "%3Asaml%3D%22urn%3Aoasis%3Anames%3Atc%3ASAML%3A2.0%3Aassertion%"
               "22%3E%40NOT_USED%40%3C%2Fsaml%3ANameID%3E%3Csamlp%3ASessionInde"
               "x%3E" ticket "%3C%2Fsamlp%3ASessionIndex%3E%3C%2Fsamlp%3ALogout"
               "Request%3E"))))

(deftest single-sign-out-ticket-test
  (is (= (single-sign-out-ticket
           (single-sign-out-request "ST-7-YZRs5ZUJmZQGcWgXwqBV-cas01.example.org"))
         "ST-7-YZRs5ZUJmZQGcWgXwqBV-cas01.example.org"))

  (is (= (single-sign-out-ticket (request :post "/"))
         nil)))

(deftest sign-out-test
  (let [session-atom (atom {"unrelated session" {:foo 123}})
        session-store (memory-store session-atom)
        returned-response {:headers {"Content-Type" "text/plain"}
                           :status 200
                           :body "Hello"
                           :session {:some [:session :data]}}
        handler (-> (constantly returned-response)
                  (wrap-session {:store session-store})
                  (wrap-cas-single-sign-out session-store))]
    ;; Make a request without an existing session, associating the newly created
    ;; session with ticket ST-7-YZRs5ZUJmZQGcWgXwqBV-cas01.example.org
    (let [received-response (-> (request :get "/")
                              (query-string {"ticket" "ST-7-YZRs5ZUJmZQGcWgXwqBV-cas01.example.org"})
                              handler)]
      ;; A new session should have been created in the session store (in
      ;; addition to the unrelated session) and its key should have been
      ;; delivered to the client in a cookie. This is all done by the session
      ;; middleware, we just make sure our middleware doesn't break anything.
      (is (re-matches #"ring-session=.*"
                      (-> received-response :headers (get "Set-Cookie") first)))
      (is (= (count @session-atom) 2))
      (is (@session-atom "unrelated session"))
      ;; Except for the session stuff, the original response should have been
      ;; returned as-is.
      (is (= (update-in received-response [:headers] dissoc "Set-Cookie")
             (dissoc returned-response :session))))
    ;; Send a CAS single sign out request.
    (is (= (handler (single-sign-out-request
                      "ST-7-YZRs5ZUJmZQGcWgXwqBV-cas01.example.org"))
           single-sign-out-response))
    ;; The login session should be destroyed, but the unrelated session should
    ;; still exist.
    (is (= (keys @session-atom) ["unrelated session"]))))

;; Single sign out should work even after an (unsuccessful) attempt to reuse the
;; ticket. (The attempt should fail because the CAS server should not validate
;; a used ticket).
(deftest sign-out-after-attempt-to-reuse-ticket-test
  (let [session-atom (atom {})
        session-store (memory-store session-atom)
        handler (-> (constantly {:status 200
                                 :session {}})
                  (wrap-session {:store session-store})
                  (wrap-cas-single-sign-out session-store))]
    ;; Sign in with a ticket.
    (-> (request :get "/")
      (query-string {"ticket" "ST-7-YZRs5ZUJmZQGcWgXwqBV-cas01.example.org"})
      handler)
    ;; A new session should have been created.
    (is (= (count @session-atom) 1))
    (let [session-key (first (keys @session-atom))]
      ;; Attempt to reuse the ticket in a new session.
      (-> (request :get "/")
        (query-string {"ticket" "ST-7-YZRs5ZUJmZQGcWgXwqBV-cas01.example.org"})
        handler)
      ;; Another session should have been created in addition to the first.
      (is (= (count @session-atom) 2))
      (is (@session-atom session-key))
      ;; Send a CAS single sign out request for the ticket.
      (is (= (handler (single-sign-out-request
                        "ST-7-YZRs5ZUJmZQGcWgXwqBV-cas01.example.org"))
             single-sign-out-response))
      ;; The first session should have been destroyed.
      (is (not (@session-atom session-key))))))

;; The middleware should work with Ring 1.4.0 and explicitly added wrap-cookies
;; middleware. See https://github.com/solita/cas-single-sign-out/issues/1
(deftest wrap-cookies-test
  (let [session-atom (atom {})
        session-store (memory-store session-atom)
        returned-response {:headers {"Content-Type" "text/plain"}
                           :status 200
                           :body "Hello"
                           :session {:some [:session :data]}}
        handler (-> (constantly returned-response)
                  ;; Add the wrap-cookies middleware.
                  wrap-cookies
                  (wrap-session {:store session-store})
                  (wrap-cas-single-sign-out session-store))]
    ;; Check that wrap-cas-single-sign-out doesn't break the wrap-cookies
    ;; middleware
    (let [received-response (-> (request :get "/")
                              handler)]
      (is (re-matches #"ring-session=.*"
                      (-> received-response :headers (get "Set-Cookie") first))))))
