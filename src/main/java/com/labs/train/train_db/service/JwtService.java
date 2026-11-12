package com.labs.train.train_db.service;

import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.labs.train.train_db.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JwtService {

        @Value("${raillens.jwt.secret:}")
        private String configuredSecret;

        @Value("${raillens.jwt.expiration-minutes:60}")
        private long expirationMinutes;

        /** Admin access tokens are short-lived: a demotion or a stolen token stops working within this window. */
        @Value("${raillens.jwt.admin-expiration-minutes:15}")
        private long adminExpirationMinutes;

        @Value("${raillens.mfa.enrolment-token-minutes:5}")
        private long enrolmentTokenMinutes;

        static final String CLAIM_ROLE = "role";
        static final String CLAIM_MFA = "mfa";
        /** Set only on single-purpose tokens (MFA enrolment). Such a token is never accepted as an access token. */
        static final String CLAIM_PURPOSE = "purpose";
        static final String PURPOSE_MFA_ENROLMENT = "mfa-enrolment";

        private SecretKey signingKey;

        @PostConstruct
        void init() {
                if (configuredSecret == null || configuredSecret.isBlank()) {
                        throw new IllegalStateException(
                                        "raillens.jwt.secret is not configured - refusing to start. "
                                                        + "Set it to a random string of at least 32 characters "
                                                        + "(see application.properties.example).");
                }

                if (configuredSecret.getBytes().length < 32) {
                        throw new IllegalStateException(
                                        "raillens.jwt.secret is too short for HS256 - it must be at least "
                                                        + "32 bytes (256 bits). Generate one with, e.g., "
                                                        + "`openssl rand -base64 32`.");
                }

                this.signingKey = Keys.hmacShaKeyFor(configuredSecret.getBytes());
        }

        public long getExpirationSeconds() {
                return expirationMinutes * 60;
        }

        /** A plain user access token (unchanged format: no role claim means USER). */
        public String generateToken(String username) {
                return build(username, expirationMinutes, null);
        }

        /**
         * Access token for a signed-in account. The ADMIN claim is only written when the session passed MFA, so an
         * admin who somehow holds a password-only session gets an ordinary user token.
         */
        public String generateToken(User user, boolean mfa) {
                if (user.isAdmin() && mfa) {
                        return build(user.getUsername(), adminExpirationMinutes,
                                        Map.of(CLAIM_ROLE, User.ROLE_ADMIN, CLAIM_MFA, true));
                }
                return generateToken(user.getUsername());
        }

        public long getExpirationSeconds(User user, boolean mfa) {
                return (user.isAdmin() && mfa ? adminExpirationMinutes : expirationMinutes) * 60;
        }

        /** Proves "password was correct" for an admin who has not set up MFA yet; only the enrol/confirm calls take it. */
        public String generateEnrolmentToken(String username) {
                return build(username, enrolmentTokenMinutes, Map.of(CLAIM_PURPOSE, PURPOSE_MFA_ENROLMENT));
        }

        public String validateEnrolmentToken(String token) {
                Claims claims = parse(token);
                if (claims == null || !PURPOSE_MFA_ENROLMENT.equals(claims.get(CLAIM_PURPOSE, String.class))) {
                        return null;
                }
                return claims.getSubject();
        }

        private String build(String subject, long minutes, Map<String, Object> claims) {
                Date now = new Date();
                Date expiry = new Date(now.getTime() + minutes * 60 * 1000);
                var builder = Jwts.builder().subject(subject).issuedAt(now).expiration(expiry);
                if (claims != null) {
                        builder.claims(claims);
                }
                return builder.signWith(signingKey).compact();
        }

        /** Access-token check: valid signature, not expired, and not a single-purpose token. Null if rejected. */
        public AccessClaims parseAccessToken(String token) {
                Claims claims = parse(token);
                if (claims == null || claims.get(CLAIM_PURPOSE) != null) {
                        return null;
                }
                boolean admin = User.ROLE_ADMIN.equals(claims.get(CLAIM_ROLE, String.class))
                                && Boolean.TRUE.equals(claims.get(CLAIM_MFA, Boolean.class));
                return new AccessClaims(claims.getSubject(), admin);
        }

        public record AccessClaims(String username, boolean admin) {
        }

        public String validateAndGetUsername(String token) {
                AccessClaims claims = parseAccessToken(token);
                return claims == null ? null : claims.username();
        }

        private Claims parse(String token) {
                try {
                        return Jwts.parser()
                                        .verifyWith(signingKey)
                                        .build()
                                        .parseSignedClaims(token)
                                        .getPayload();
                } catch (JwtException | IllegalArgumentException ex) {
                        log.debug("Rejecting invalid JWT: {}", ex.getMessage());
                        return null;
                }
        }
}
