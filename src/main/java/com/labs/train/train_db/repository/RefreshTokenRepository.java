package com.labs.train.train_db.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.labs.train.train_db.entity.RefreshToken;
import com.labs.train.train_db.entity.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

        Optional<RefreshToken> findByTokenHash(String tokenHash);

        /**
         * Row lock for rotation. Without it two concurrent refreshes with the same token both saw it valid, both
         * revoked it and both issued a successor: two live sessions from one token.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash")
        Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String tokenHash);

        @Modifying
        @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.familyId = :family AND rt.revoked = false")
        int revokeFamily(@Param("family") UUID familyId);

        @Modifying
        @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
        void revokeAllForUser(@Param("user") User user);

        void deleteByUser(User user);

        /**
         * Expired tokens, and tokens revoked by logout. ROTATED tokens are kept until they expire: they are what
         * reuse detection matches a replayed token against. (Deleting every revoked token, as before, would turn
         * a stolen-token replay into a plain "unknown token" and hide the theft.)
         */
        @Modifying
        @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR (rt.revoked = true AND rt.rotatedAt IS NULL)")
        int deleteExpiredOrLoggedOut(@Param("now") LocalDateTime now);
}
