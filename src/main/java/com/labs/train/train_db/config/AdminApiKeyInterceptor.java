package com.labs.train.train_db.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Gates every {@code /api/v1/admin/**} route behind a shared-secret header.
 *
 * There is currently no Spring Security dependency anywhere in this
 * project, which means the bulk CSV import endpoint - the one route that
 * deletes and rewrites train schedule data - was completely open to anyone
 * who could reach the API. This interceptor is a deliberately minimal,
 * fail-closed stopgap for that specific route, not a replacement for real
 * authentication. Whether the write endpoints on /api/v1/trains,
 * /api/v1/stations and /api/v1/schedules also need auth (and what scheme: session, JWT, API
 * key per-client for the future mobile apps) is a bigger decision that
 * needs the user's input before implementing - see the enhancement summary.
 * (Public API versioning under /api/v1/** happened first - see
 * project memory - but that's orthogonal to this decision.)
 *
 * Fails closed: if {@code raillens.admin.api-key} isn't configured, every
 * request to /api/v1/admin/** is rejected rather than silently left open.
 */
@Slf4j
@Component
public class AdminApiKeyInterceptor implements HandlerInterceptor {

        private static final String HEADER_NAME = "X-Admin-Key";

        @Value("${raillens.admin.api-key:}")
        private String configuredKey;

        @Override
        public boolean preHandle(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Object handler) throws Exception {

                // Same reasoning as JwtAuthInterceptor's OPTIONS check - the
                // browser's CORS preflight for a request carrying
                // X-Admin-Key never includes that header itself, so
                // rejecting it here would fail the preflight and block the
                // real request before it's sent, looking like the backend
                // is unreachable rather than a 401/503.
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                        return true;
                }

                if (configuredKey == null || configuredKey.isBlank()) {
                        log.warn(
                                        "Rejecting {} {} - raillens.admin.api-key is not configured",
                                        request.getMethod(), request.getRequestURI());
                        reject(response, HttpStatus.SERVICE_UNAVAILABLE, "Admin API is not configured");
                        return false;
                }

                String provided = request.getHeader(HEADER_NAME);

                if (!constantTimeEquals(configuredKey, provided)) {
                        log.warn(
                                        "Rejecting {} {} - missing or invalid {} header",
                                        request.getMethod(), request.getRequestURI(), HEADER_NAME);
                        reject(response, HttpStatus.UNAUTHORIZED, "Missing or invalid " + HEADER_NAME + " header");
                        return false;
                }

                return true;
        }

        /**
         * {@code String.equals} short-circuits on the first differing byte,
         * so comparing a guessed key against the real one this way leaks
         * timing information an attacker could use to recover the key one
         * byte at a time. {@link MessageDigest#isEqual} always compares the
         * full length of both inputs, so it takes the same time whether the
         * first byte or the last byte is wrong. Only meaningful because this
         * key is a long-lived shared secret checked on every admin request -
         * not needed for e.g. one-time tokens.
         */
        private boolean constantTimeEquals(String expected, String actual) {
                if (actual == null) {
                        return false;
                }
                return MessageDigest.isEqual(
                                expected.getBytes(StandardCharsets.UTF_8),
                                actual.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Writes the same {@code {timestamp, status, error}} shape as {@code
         * ApiErrorResponse} (see {@code GlobalExceptionHandler}) so clients get
         * a consistent error contract regardless of whether a request was
         * rejected here or by a controller. Built by hand rather than via
         * Jackson's {@code ObjectMapper}: {@code spring-boot-starter-webmvc}
         * doesn't pull Jackson onto the classpath by itself in Spring Boot 4
         * (that now requires the separate {@code spring-boot-starter-jackson}
         * starter), and this body is simple and fixed-shape enough that it's
         * not worth adding a dependency for.
         */
        private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {

                String escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");

                String json = "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\"}"
                                .formatted(LocalDateTime.now(), status.value(), escapedMessage);

                response.setStatus(status.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(json);
        }
}
