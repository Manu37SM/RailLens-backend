package com.labs.train.train_db.config;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.labs.train.train_db.service.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gates any route registered against it (currently {@code /api/v1/auth/**}
 * minus register/login - see WebConfig) behind a valid {@code
 * Authorization: Bearer <jwt>} header. On success, stashes the resolved
 * username as a request attribute
 * so the controller doesn't need to touch {@link JwtService} directly -
 * same separation of concerns as {@code AdminApiKeyInterceptor} keeping key
 * validation out of the controller layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

        private static final String BEARER_PREFIX = "Bearer ";

        private final JwtService jwtService;

        @Override
        public boolean preHandle(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Object handler) throws Exception {

                // CORS preflight (OPTIONS) requests never carry the
                // Authorization header - the browser sends it bare on
                // purpose, before deciding whether the real request is even
                // allowed. Rejecting it here would fail the preflight
                // itself, which blocks the real request client-side before
                // it's ever sent - surfacing to the frontend as a generic
                // "can't reach the server" network error, not a 401, making
                // it look like the backend is down when it's actually this
                // interceptor answering a request the browser was never
                // going to attach credentials to. Must run before the
                // Authorization check, not after - there's nothing to
                // validate on a preflight.
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                        return true;
                }

                String header = request.getHeader("Authorization");

                if (header == null || !header.startsWith(BEARER_PREFIX)) {
                        reject(response, "Missing or invalid Authorization header");
                        return false;
                }

                String token = header.substring(BEARER_PREFIX.length());
                String username = jwtService.validateAndGetUsername(token);

                if (username == null) {
                        reject(response, "Invalid or expired token");
                        return false;
                }

                request.setAttribute("authenticatedUsername", username);
                return true;
        }

        private void reject(HttpServletResponse response, String message) throws IOException {

                String escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");

                String json = "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\"}"
                                .formatted(LocalDateTime.now(), HttpStatus.UNAUTHORIZED.value(), escapedMessage);

                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(json);
        }
}
