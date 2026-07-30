package com.labs.train.train_db.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.RefreshToken;
import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Issues and rotates the long-lived refresh tokens that let the frontend
 * get a fresh access JWT (see JwtService) without asking the user to log
 * in again every {@code raillens.jwt.expiration-minutes}. See
 * RefreshToken's javadoc for the storage/rotation model.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

        private static final SecureRandom RANDOM = new SecureRandom();

        private final RefreshTokenRepository refreshTokenRepository;

        @Value("${raillens.jwt.refresh-expiration-days:30}")
        private long refreshExpirationDays;

        /**
         * Generates a new opaque token, persists only its hash, and returns
         * the raw value - the only time the raw value ever exists outside the
         * caller's response, exactly like a password is only ever seen once
         * before being hashed.
         */
        @Transactional
        public String issue(User user) {

                String rawToken = generateRawToken();

                RefreshToken entity = new RefreshToken(
                                user,
                                hash(rawToken),
                                LocalDateTime.now().plusDays(refreshExpirationDays));

                refreshTokenRepository.save(entity);

                return rawToken;
        }

        /**
         * Validates a raw refresh token, revokes it (rotation - see class
         * javadoc), and issues a replacement for the same user. Throws
         * InvalidCredentialsException if the token is unknown, already used,
         * or expired - deliberately the same exception/message shape as a
         * failed login, so a client can't distinguish "bad refresh token"
         * from "bad password" and use that to enumerate anything.
         */
        @Transactional
        public RotatedToken rotate(String rawToken) {

                // Explicit lambda rather than RefreshToken::isValid - avoids the
                // JDT null analyzer's "unchecked conversion for the receiver"
                // warning on the method-reference form; same behavior either way.
                RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                                .filter(token -> token.isValid())
                                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

                existing.setRevoked(true);
                refreshTokenRepository.save(existing);

                User user = existing.getUser();
                String newRawToken = issue(user);

                return new RotatedToken(user, newRawToken);
        }

        /**
         * Revokes a single refresh token (used on logout) - a no-op if it's
         * already invalid/unknown, since "logging out" should never itself be
         * a failable action from the client's perspective.
         */
        @Transactional
        public void revoke(String rawToken) {
                refreshTokenRepository.findByTokenHash(hash(rawToken))
                                .ifPresent(token -> {
                                        token.setRevoked(true);
                                        refreshTokenRepository.save(token);
                                });
        }

        /**
         * Revokes every outstanding refresh token for a user - called when
         * the password changes or the account is deleted, so a token issued
         * before either event stops working (see AuthService).
         */
        @Transactional
        public void revokeAllForUser(User user) {
                refreshTokenRepository.revokeAllForUser(user);
        }

        /**
         * Hard-deletes every refresh token row for a user - must run before
         * the User row itself is deleted (see AuthService#deleteAccount) since
         * the FK from refresh_tokens to users would otherwise block it.
         */
        @Transactional
        public void deleteAllForUser(User user) {
                refreshTokenRepository.deleteByUser(user);
        }

        private String generateRawToken() {
                byte[] bytes = new byte[32];
                RANDOM.nextBytes(bytes);
                return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        private String hash(String rawToken) {
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        return HexFormat.of().formatHex(digest.digest(rawToken.getBytes()));
                } catch (NoSuchAlgorithmException ex) {
                        // SHA-256 is guaranteed available on every JVM - this can't happen.
                        throw new IllegalStateException("SHA-256 unavailable", ex);
                }
        }

        public record RotatedToken(User user, String rawToken) {
        }
}
