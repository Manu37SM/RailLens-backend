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

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private LocalDateTime lockedUntil;

    /** USER or ADMIN (V7). Admin rights also require an MFA session; see JwtAuthenticationFilter. */
    @Column(nullable = false, length = 16)
    private String role = ROLE_USER;

    /** AES-256-GCM ciphertext of the TOTP secret (MfaSecretCipher); null until enrolment starts. */
    private byte[] mfaSecret;

    @Column(nullable = false)
    private boolean mfaEnabled = false;

    /**
     * Last accepted TOTP time step: each code works once. Written ONLY by UserRepository.claimMfaStep (a conditional
     * UPDATE): AuthService saves detached User objects, and a full merge carrying a stale value here would let an
     * already-used code work again.
     */
    @Column(insertable = false, updatable = false)
    private Long mfaLastStep;

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

}
