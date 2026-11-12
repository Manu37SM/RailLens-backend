package com.labs.train.train_db.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.labs.train.train_db.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates {@code Authorization: Bearer <jwt>} (same tokens as before). ROLE_ADMIN is added only for an admin
 * token that passed MFA; single-purpose tokens (MFA enrolment) are rejected here. Authentication only: whether the
 * endpoint needs a user is decided by SecurityConfig, so a bad token on a PUBLIC read is ignored exactly as before.
 *
 * <p>Sets the {@code authenticatedUsername} request attribute the controllers already read, so no controller changes.
 *
 * <p>Deliberately NOT a @Component: Spring Boot would also register it as a plain servlet filter, which runs first
 * and makes OncePerRequestFilter skip the in-chain copy, so the authentication would be lost.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

        static final String USERNAME_ATTRIBUTE = "authenticatedUsername";
        private static final String BEARER = "Bearer ";

        private final JwtService jwtService;

        public JwtAuthenticationFilter(JwtService jwtService) {
                this.jwtService = jwtService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith(BEARER)) {
                        JwtService.AccessClaims claims = jwtService.parseAccessToken(header.substring(BEARER.length()));
                        if (claims != null) {
                                // ADMIN only from a token minted after password + one-time code (see JwtService).
                                List<SimpleGrantedAuthority> roles = claims.admin()
                                                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                                                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                                var auth = new UsernamePasswordAuthenticationToken(claims.username(), null, roles);
                                SecurityContextHolder.getContext().setAuthentication(auth);
                                request.setAttribute(USERNAME_ATTRIBUTE, claims.username());
                        } else {
                                // Read by JsonAuthErrorHandler: keeps the old, more specific 401 message.
                                request.setAttribute(JsonAuthErrorHandler.REASON_ATTRIBUTE, "Invalid or expired token");
                        }
                }
                chain.doFilter(request, response);
        }
}
