package com.labs.train.train_db.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code X-Admin-Key} grants ROLE_ADMIN. A WRONG key is rejected immediately (401), not downgraded to anonymous,
 * so a typo never silently falls through to a public response. Constant-time comparison, as before.
 * Phase 2 replaces the shared key with per-person admin accounts + MFA.
 * Not a @Component, for the same reason as JwtAuthenticationFilter.
 */
@Slf4j
public class AdminKeyAuthenticationFilter extends OncePerRequestFilter {

        static final String HEADER = "X-Admin-Key";

        private final JsonAuthErrorHandler errors;
        private final String configuredKey;

        public AdminKeyAuthenticationFilter(JsonAuthErrorHandler errors, String configuredKey) {
                this.errors = errors;
                this.configuredKey = configuredKey;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                String provided = request.getHeader(HEADER);
                if (provided == null) {
                        chain.doFilter(request, response);
                        return;
                }
                if (configuredKey == null || configuredKey.isBlank()) {
                        log.warn("Rejecting {} {} - raillens.admin.api-key is not configured",
                                        request.getMethod(), request.getRequestURI());
                        errors.write(response, HttpStatus.SERVICE_UNAVAILABLE, "Admin API is not configured");
                        return;
                }
                if (!MessageDigest.isEqual(configuredKey.getBytes(StandardCharsets.UTF_8),
                                provided.getBytes(StandardCharsets.UTF_8))) {
                        log.warn("Rejecting {} {} - invalid {} header", request.getMethod(), request.getRequestURI(), HEADER);
                        errors.write(response, HttpStatus.UNAUTHORIZED, "Missing or invalid " + HEADER + " header");
                        return;
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                "admin-api-key", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                chain.doFilter(request, response);
        }
}
