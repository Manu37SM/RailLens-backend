package com.labs.train.train_db.security;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.labs.train.train_db.exception.AuthFlowException;

import lombok.extern.slf4j.Slf4j;

/**
 * Phase 4D: once a profile is linked to a RailOps login, its password lives in RailOps. Changing the password or
 * closing the account is forwarded there with the user's own RailOps access token; RailOps checks the password
 * (and counts wrong guesses toward its lockout), so RailLens never needs a copy of it.
 *
 * <p>Calls {@code raillens.railops.api-url}, which defaults to the issuer (the RailOps identity service URL).
 * Every failure becomes an {@link AuthFlowException} with a status the website can show as-is.
 */
@Slf4j
@Component
public class RailOpsAccountClient {

        private final RestClient http;

        public RailOpsAccountClient(@Value("${raillens.railops.api-url:${raillens.railops.issuer:}}") String apiUrl) {
                if (apiUrl == null || apiUrl.isBlank()) {
                        this.http = null;
                        return;
                }
                var factory = new SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(Duration.ofSeconds(5));
                factory.setReadTimeout(Duration.ofSeconds(20));   // a sleeping free RailOps can be slow to answer once
                this.http = RestClient.builder().baseUrl(apiUrl.trim().replaceAll("/+$", "")).requestFactory(factory).build();
        }

        public void changePassword(String railOpsToken, String currentPassword, String newPassword) {
                post("/api/v1/auth/password", railOpsToken, Map.of("currentPassword", currentPassword, "newPassword", newPassword));
        }

        public void erase(String railOpsToken, String password) {
                post("/api/v1/auth/erase", railOpsToken, Map.of("password", password));
        }

        private void post(String path, String token, Map<String, String> body) {
                if (http == null) {
                        throw unavailable();
                }
                try {
                        http.post().uri(path)
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(body)
                                        .retrieve()
                                        .onStatus(HttpStatusCode::isError, (req, res) -> {
                                                throw mapped(res.getStatusCode().value());
                                        })
                                        .toBodilessEntity();
                } catch (AuthFlowException e) {
                        throw e;
                } catch (RestClientException e) {
                        log.warn("RailOps account call {} failed: {}", path, e.getClass().getSimpleName());
                        throw unavailable();
                }
        }

        /** RailOps' own messages are not passed through: they can change, and some are worded for its own web app. */
        static AuthFlowException mapped(int status) {
                return switch (status) {
                case 401 -> new AuthFlowException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                                "Password is incorrect, or the account is temporarily locked");
                case 422 -> new AuthFlowException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
                                "Choose a stronger password that is different from the current one");
                case 403 -> new AuthFlowException(HttpStatus.FORBIDDEN, "NOT_ALLOWED",
                                "This account can't be changed here");
                case 429 -> new AuthFlowException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                                "Too many attempts. Try again in a minute");
                default -> unavailable();
                };
        }

        static AuthFlowException unavailable() {
                return new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "RAILOPS_UNAVAILABLE",
                                "Sign-in service is unavailable. Try again shortly");
        }
}
