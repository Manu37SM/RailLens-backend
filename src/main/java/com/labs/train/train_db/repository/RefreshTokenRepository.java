package com.labs.train.train_db.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.labs.train.train_db.entity.RefreshToken;
import com.labs.train.train_db.entity.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

        Optional<RefreshToken> findByTokenHash(String tokenHash);

        @Modifying
        @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
        void revokeAllForUser(@Param("user") User user);

        // Hard delete, not revoke - used right before the User row itself is
        // deleted (see AuthService#deleteAccount), since the FK from
        // refresh_tokens to users would otherwise block that delete. Derived
        // delete query - no @Modifying needed (that's only for @Query-backed
        // bulk operations, like revokeAllForUser above).
        void deleteByUser(User user);

        // Rotation (see RefreshTokenService#rotate) only ever flips `revoked`
        // to true, never deletes the row - so without this, the table grows
        // by one row on every login and every refresh, forever (see the
        // backend audit's "unbounded refresh_tokens growth" finding).
        // Naturally-expired-but-never-used tokens (revoked = false, past
        // expiresAt) are just as safe to remove - RefreshToken#isValid()
        // already treats them as unusable.
        @Modifying
        @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true OR rt.expiresAt < :now")
        int deleteRevokedOrExpired(@Param("now") LocalDateTime now);
}
