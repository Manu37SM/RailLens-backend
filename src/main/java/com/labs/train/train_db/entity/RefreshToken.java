package com.labs.train.train_db.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A long-lived, opaque bearer credential that can be exchanged for a fresh
 * short-lived access JWT (see JwtService/RefreshTokenService) without the
 * user re-entering their password. Only the SHA-256 hash of the raw token
 * is stored - same reasoning as never storing a plaintext password - so a
 * database leak alone doesn't hand out working refresh tokens.
 *
 * Single-use: RefreshTokenService revokes the old row and issues a new one
 * on every refresh ("rotation"), so a stolen-and-reused refresh token is
 * detectable (the legitimate owner's next refresh will fail because the
 * token was already consumed).
 */
@Entity
@Table(
                name = "refresh_tokens",
                indexes = { @Index(name = "idx_refresh_token_hash", columnList = "tokenHash", unique = true) })
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        @Column(nullable = false, unique = true, length = 64)
        private String tokenHash;

        @Column(nullable = false)
        private LocalDateTime expiresAt;

        @Column(nullable = false)
        private boolean revoked = false;

        @Column(nullable = false)
        private LocalDateTime createdAt = LocalDateTime.now();

        public RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
                this.user = user;
                this.tokenHash = tokenHash;
                this.expiresAt = expiresAt;
        }

        public boolean isValid() {
                return !revoked && expiresAt.isAfter(LocalDateTime.now());
        }
}
