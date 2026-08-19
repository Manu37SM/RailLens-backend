package com.labs.train.train_db.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    // BCrypt hash only - the plaintext password is never persisted or
    // logged anywhere (see AuthService). Named explicitly "passwordHash"
    // rather than "password" so it can never be accidentally returned
    // as-is by a DTO that just forwards entity field names.
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Account lockout (see AuthService#login and
    // V4__add_account_lockout_fields.sql). failedLoginAttempts resets to 0
    // on any successful login; lockedUntil is null unless the account is
    // currently locked out, in which case login is rejected until that
    // timestamp passes.
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private LocalDateTime lockedUntil;

    // No roles/admin distinction yet - every registered user is a plain
    // user. The existing POST /api/admin/import route has its own
    // separate shared-secret gate (AdminApiKeyInterceptor) unrelated to
    // this table. Introduce a role column when there's an actual need for
    // more than one permission level rather than guessing at one now.
}
