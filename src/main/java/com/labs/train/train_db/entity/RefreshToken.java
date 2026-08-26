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
