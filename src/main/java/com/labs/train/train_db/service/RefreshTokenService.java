package com.labs.train.train_db.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.RefreshToken;
import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.repository.RefreshTokenRepository;

import com.labs.train.train_db.audit.AuditActions;
import com.labs.train.train_db.audit.SecurityAuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

        private static final SecureRandom RANDOM = new SecureRandom();

        private final RefreshTokenRepository refreshTokenRepository;
        private final SecurityAuditService audit;

        @Value("${raillens.jwt.refresh-expiration-days:30}")
        private long refreshExpirationDays;

        /**
         * MFA (admin) sessions end this long after the one-time code was entered, however often they refresh: the
         * expiry is fixed at login and carried forward, not slid. Ordinary sessions keep the sliding 30 days.
         */
        @Value("${raillens.auth.mfa-session-hours:12}")
        private long mfaSessionHours;

        /**
         * Two tabs (or an app and its widget) refreshing at the same moment both present the same token; the second
         * arrives just after the first rotated it. Within this window that is treated as a race, not a theft.
         */
        @Value("${raillens.auth.refresh-reuse-grace-seconds:10}")
        private long reuseGraceSeconds;

        /** Starts a new session (login, register): a new token family. */
        @Transactional
        public String issue(User user) {
                return issue(user, false);
        }

        /** As {@link #issue(User)}; {@code mfa} records that the session passed a one-time code (carried on rotation). */
        @Transactional
        public String issue(User user, boolean mfa) {
                LocalDateTime expiresAt = mfa
                                ? LocalDateTime.now().plusHours(mfaSessionHours)
                                : LocalDateTime.now().plusDays(refreshExpirationDays);
                return issue(user, UUID.randomUUID(), mfa, expiresAt);
        }

        private String issue(User user, UUID familyId, boolean mfa, LocalDateTime expiresAt) {
                String rawToken = generateRawToken();
                RefreshToken entity = new RefreshToken(
                                user,
                                hash(rawToken),
                                expiresAt,
                                familyId);
                entity.setMfa(mfa);
                refreshTokenRepository.save(entity);
                return rawToken;
        }

        /**
         * Exchanges a refresh token for its successor in the same family.
         *
         * <p>Reuse detection: a token that was already rotated is only presented again if a copy exists somewhere else.
         * Outside the grace window the whole family is revoked (the thief's and the user's tokens alike) and the user
         * must log in again. Previously a stolen token simply raced the user, and whoever refreshed first kept the
         * session for 30 days.
         *
         * <p>{@code noRollbackFor}: the family revocation must COMMIT even though the call ends in an exception.
         */
        @Transactional(noRollbackFor = InvalidCredentialsException.class)
        public RotatedToken rotate(String rawToken) {
                RefreshToken existing = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                                .orElseThrow(RefreshTokenService::invalid);
                LocalDateTime now = LocalDateTime.now();

                if (existing.isRevoked()) {
                        LocalDateTime rotatedAt = existing.getRotatedAt();
                        if (rotatedAt != null && rotatedAt.isBefore(now.minusSeconds(reuseGraceSeconds))) {
                                int revoked = refreshTokenRepository.revokeFamily(existing.getFamilyId());
                                log.warn("Refresh token reuse detected for user id {} (family {}): revoked {} token(s)",
                                                existing.getUser().getId(), existing.getFamilyId(), revoked);
                                audit.record(existing.getUser().getUsername(), AuditActions.REFRESH_TOKEN_REUSED,
                                                existing.getUser().getUsername(), "revoked " + revoked + " session token(s)");
                        }
                        throw invalid();
                }
                if (!existing.getExpiresAt().isAfter(now)) {
                        throw invalid();
                }

                existing.setRevoked(true);
                existing.setRotatedAt(now);
                refreshTokenRepository.save(existing);

                User user = existing.getUser();
                Hibernate.initialize(user);

                LocalDateTime expiresAt = existing.isMfa()
                                ? existing.getExpiresAt()
                                : now.plusDays(refreshExpirationDays);
                String newRawToken = issue(user, existing.getFamilyId(), existing.isMfa(), expiresAt);
                return new RotatedToken(user, newRawToken, existing.isMfa());
        }

        private static InvalidCredentialsException invalid() {
                return new InvalidCredentialsException("Invalid or expired refresh token");
        }

        /** Logout ends the whole session: every token in the presented token's family. */
        @Transactional
        public void revoke(String rawToken) {
                refreshTokenRepository.findByTokenHash(hash(rawToken))
                                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId()));
        }

        @Transactional
        public void revokeAllForUser(User user) {
                refreshTokenRepository.revokeAllForUser(user);
        }

        @Transactional
        public void deleteAllForUser(User user) {
                refreshTokenRepository.deleteByUser(user);
        }

        @Transactional
        public int purgeExpiredAndLoggedOut() {
                return refreshTokenRepository.deleteExpiredOrLoggedOut(LocalDateTime.now());
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
                        throw new IllegalStateException("SHA-256 unavailable", ex);
                }
        }

        public record RotatedToken(User user, String rawToken, boolean mfa) {
        }
}
