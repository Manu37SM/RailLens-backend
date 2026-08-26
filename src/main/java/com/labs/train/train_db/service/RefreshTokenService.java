package com.labs.train.train_db.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.RefreshToken;
import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

        private static final SecureRandom RANDOM = new SecureRandom();

        private final RefreshTokenRepository refreshTokenRepository;

        @Value("${raillens.jwt.refresh-expiration-days:30}")
        private long refreshExpirationDays;

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

        @Transactional
        public RotatedToken rotate(String rawToken) {

                RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                                .filter(token -> token.isValid())
                                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

                existing.setRevoked(true);
                refreshTokenRepository.save(existing);

                User user = existing.getUser();

                Hibernate.initialize(user);

                String newRawToken = issue(user);

                return new RotatedToken(user, newRawToken);
        }

        @Transactional
        public void revoke(String rawToken) {
                refreshTokenRepository.findByTokenHash(hash(rawToken))
                                .ifPresent(token -> {
                                        token.setRevoked(true);
                                        refreshTokenRepository.save(token);
                                });
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
        public int purgeRevokedOrExpired() {
                return refreshTokenRepository.deleteRevokedOrExpired(LocalDateTime.now());
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

        public record RotatedToken(User user, String rawToken) {
        }
}
