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
 * LEGACY: the shared {@code X-Admin-Key} grants ROLE_ADMIN. Replaced by per-person admin accounts with MFA (V7);
 * kept during the transition while {@code raillens.admin.legacy-key-enabled=true} so deployed clients keep working,
 * logging a warning on each use. With the flag false, any request carrying the header is refused with a message
 * telling the caller to sign in with an admin account.
 *
 * <p>A WRONG key is rejected immediately (401), not downgraded to anonymous, so a typo never silently falls through
 * to a public response. Constant-time comparison. Not a @Component, for the same reason as JwtAuthenticationFilter.
 */
@Slf4j
public class AdminKeyAuthenticationFilter extends OncePerRequestFilter {

        static final String HEADER = "X-Admin-Key";

        private final JsonAuthErrorHandler errors;
        static final String DISABLED_MESSAGE = HEADER + " is no longer accepted - sign in with an admin account";

        private final String configuredKey;
        private final boolean enabled;

        public AdminKeyAuthenticationFilter(JsonAuthErrorHandler errors, String configuredKey, boolean enabled) {
                this.errors = errors;
                this.configuredKey = configuredKey;
                this.enabled = enabled;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                String provided = request.getHeader(HEADER);
                if (provided == null) {
                        chain.doFilter(request, response);
                        return;
                }
                if (!enabled) {
                        log.warn("Rejecting {} {} - legacy {} header used but disabled",
                                        request.getMethod(), request.getRequestURI(), HEADER);
                        errors.write(response, HttpStatus.UNAUTHORIZED, DISABLED_MESSAGE);
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
                log.warn("Legacy {} used for {} {} - move this client to an admin account with MFA, then set "
                                + "raillens.admin.legacy-key-enabled=false", HEADER, request.getMethod(), request.getRequestURI());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                "admin-api-key", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                chain.doFilter(request, response);
        }
}
