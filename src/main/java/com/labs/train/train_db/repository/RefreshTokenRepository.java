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

        void deleteByUser(User user);

        @Modifying
        @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true OR rt.expiresAt < :now")
        int deleteRevokedOrExpired(@Param("now") LocalDateTime now);
}
