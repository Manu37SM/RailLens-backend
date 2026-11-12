package com.labs.train.train_db.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.labs.train.train_db.security.RailOpsAccountLinker;
import com.labs.train.train_db.security.RailOpsTokenVerifier;
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
 * <p>Also accepts RailOps access tokens (RS256, single login, Phase 4B) when {@link RailOpsTokenVerifier} is
 * configured. They map to a RailLens profile through {@link RailOpsAccountLinker}; ROLE_ADMIN only for a RailOps
 * nationwide admin who signed in with a one-time code.
 *
 * <p>Sets the {@code authenticatedUsername} request attribute the controllers already read, so no controller changes.
 *
 * <p>Deliberately NOT a @Component: Spring Boot would also register it as a plain servlet filter, which runs first
 * and makes OncePerRequestFilter skip the in-chain copy, so the authentication would be lost.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

        static final String USERNAME_ATTRIBUTE = "authenticatedUsername";
        private static final String BEARER = "Bearer ";

        static final String EMAIL_CONFLICT_MESSAGE = "This email already has a RailLens account that isn't linked to "
                        + "your RailOps login yet. Sign in with your RailLens password for now.";

        private final JwtService jwtService;
        private final RailOpsTokenVerifier railOps;
        private final RailOpsAccountLinker linker;

        public JwtAuthenticationFilter(JwtService jwtService) {
                this(jwtService, null, null);
        }

        public JwtAuthenticationFilter(JwtService jwtService, RailOpsTokenVerifier railOps, RailOpsAccountLinker linker) {
                this.jwtService = jwtService;
                this.railOps = railOps;
                this.linker = linker;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith(BEARER) && railOps != null && railOps.isEnabled()
                                && isRs256(header.substring(BEARER.length()))) {
                        authenticateRailOps(request, header.substring(BEARER.length()));
                } else if (header != null && header.startsWith(BEARER)) {
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

        private void authenticateRailOps(HttpServletRequest request, String token) {
                var verified = railOps.verify(token);
                if (verified.isEmpty()) {
                        request.setAttribute(JsonAuthErrorHandler.REASON_ATTRIBUTE, "Invalid or expired token");
                        return;
                }
                RailOpsAccountLinker.Result result = linker.resolve(verified.get());
                if (result.outcome() == RailOpsAccountLinker.Outcome.EMAIL_CONFLICT) {
                        request.setAttribute(JsonAuthErrorHandler.REASON_ATTRIBUTE, EMAIL_CONFLICT_MESSAGE);
                        return;
                }
                String username = result.user().getUsername();
                List<SimpleGrantedAuthority> roles = verified.get().admin()
                                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null, roles));
                request.setAttribute(USERNAME_ATTRIBUTE, username);
        }

        /** RailLens' own tokens are HS256; RailOps' are RS256. Only the header is read here, never trusted. */
        static boolean isRs256(String token) {
                int dot = token.indexOf('.');
                if (dot <= 0) {
                        return false;
                }
                try {
                        String header = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
                        return header.replace(" ", "").contains("\"alg\":\"RS256\"");
                } catch (IllegalArgumentException e) {
                        return false;
                }
        }
}
