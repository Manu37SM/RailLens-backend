package com.labs.train.train_db.service;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

        public String generateToken(String username) {

                Date now = new Date();
                Date expiry = new Date(now.getTime() + expirationMinutes * 60 * 1000);

                return Jwts.builder()
                                .subject(username)
                                .issuedAt(now)
                                .expiration(expiry)
                                .signWith(signingKey)
                                .compact();
        }

        public String validateAndGetUsername(String token) {
                try {
                        Claims claims = Jwts.parser()
                                        .verifyWith(signingKey)
                                        .build()
                                        .parseSignedClaims(token)
                                        .getPayload();

                        return claims.getSubject();
                } catch (JwtException | IllegalArgumentException ex) {
                        log.debug("Rejecting invalid JWT: {}", ex.getMessage());
                        return null;
                }
        }
}
