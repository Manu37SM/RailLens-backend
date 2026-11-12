package com.labs.train.train_db.config;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401/403 bodies in the same {timestamp,status,error} shape as ApiErrorResponse, with the same messages the
 * interceptors used, because the web and mobile apps show the server's error text (mobile: lib/apiError.ts).
 */
@Component
public class JsonAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

        static final String REASON_ATTRIBUTE = JsonAuthErrorHandler.class.getName() + ".reason";

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
                        throws IOException {
                write(response, HttpStatus.UNAUTHORIZED, unauthenticatedMessage(request));
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
                        throws IOException {
                // Authenticated but not allowed, e.g. a user token on an admin route, or an unlisted (denyAll) route.
                boolean signedIn = request.getAttribute(JwtAuthenticationFilter.USERNAME_ATTRIBUTE) != null;
                if (signedIn && isAdminRoute(request)) {
                        write(response, HttpStatus.UNAUTHORIZED, "Missing or invalid " + AdminKeyAuthenticationFilter.HEADER + " header");
                        return;
                }
                write(response, HttpStatus.FORBIDDEN, "Access denied");
        }

        private static String unauthenticatedMessage(HttpServletRequest request) {
                Object reason = request.getAttribute(REASON_ATTRIBUTE);
                if (reason != null) {
                        return reason.toString();
                }
                if (isAdminRoute(request)) {
                        return "Missing or invalid " + AdminKeyAuthenticationFilter.HEADER + " header";
                }
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (path.startsWith("/api/v1/auth/")) {
                        return "Missing or invalid Authorization header";
                }
                return "Authentication required";   // unlisted route: denied by default
        }

        /** Routes that need the admin key: /api/v1/admin/** and writes to the catalogue. */
        static boolean isAdminRoute(HttpServletRequest request) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (path.startsWith("/api/v1/admin/")) {
                        return true;
                }
                boolean write = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
                return write && (path.startsWith("/api/v1/trains") || path.startsWith("/api/v1/stations")
                                || path.startsWith("/api/v1/schedules"));
        }

        void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
                String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
                response.setStatus(status.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\"}"
                                .formatted(LocalDateTime.now(), status.value(), escaped));
        }
}
