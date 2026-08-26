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

        private boolean constantTimeEquals(String expected, String actual) {
                if (actual == null) {
                        return false;
                }
                return MessageDigest.isEqual(
                                expected.getBytes(StandardCharsets.UTF_8),
                                actual.getBytes(StandardCharsets.UTF_8));
        }

        private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {

                String escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");

                String json = "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\"}"
                                .formatted(LocalDateTime.now(), status.value(), escapedMessage);

                response.setStatus(status.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(json);
        }
}
