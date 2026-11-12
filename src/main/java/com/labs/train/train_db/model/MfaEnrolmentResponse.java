package com.labs.train.train_db.model;

/**
 * What an authenticator app needs: {@code otpauthUri} for a QR code, {@code secret} (base32) for manual entry.
 * Shown once; the server only keeps it encrypted.
 */
public record MfaEnrolmentResponse(String secret, String otpauthUri) {
}
