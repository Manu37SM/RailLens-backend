package com.labs.train.train_db.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts TOTP secrets at rest (AES-256-GCM, random 96-bit IV, the user id as associated data so a ciphertext
 * copied onto another user's row fails to decrypt). A database dump alone therefore can't mint codes.
 *
 * <p>Key: {@code raillens.mfa.encryption-key}, base64 of 32 random bytes ({@code openssl rand -base64 32}).
 * Absent key: MFA operations fail with a clear error; the rest of the app runs normally.
 */
@Component
public class MfaSecretCipher {

        private static final SecureRandom RANDOM = new SecureRandom();
        private static final int IV_BYTES = 12;
        private static final int TAG_BITS = 128;

        private final SecretKeySpec key;

        public MfaSecretCipher(@Value("${raillens.mfa.encryption-key:}") String base64Key) {
                if (base64Key == null || base64Key.isBlank()) {
                        this.key = null;
                        return;
                }
                byte[] raw = Base64.getDecoder().decode(base64Key.trim());
                if (raw.length != 32) {
                        throw new IllegalStateException("raillens.mfa.encryption-key must be 32 bytes (base64), got " + raw.length);
                }
                this.key = new SecretKeySpec(raw, "AES");
        }

        public boolean isConfigured() {
                return key != null;
        }

        public byte[] encrypt(byte[] plaintext, long userId) {
                try {
                        byte[] iv = new byte[IV_BYTES];
                        RANDOM.nextBytes(iv);
                        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
                        cipher.updateAAD(aad(userId));
                        byte[] ct = cipher.doFinal(plaintext);
                        return ByteBuffer.allocate(IV_BYTES + ct.length).put(iv).put(ct).array();
                } catch (GeneralSecurityException e) {
                        throw new IllegalStateException("MFA secret encryption failed", e);
                }
        }

        public byte[] decrypt(byte[] stored, long userId) {
                try {
                        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
                        cipher.updateAAD(aad(userId));
                        return cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
                } catch (GeneralSecurityException e) {
                        throw new IllegalStateException("MFA secret could not be decrypted (wrong key or tampered row)", e);
                }
        }

        private SecretKeySpec requireKey() {
                if (key == null) {
                        throw new MfaNotConfiguredException();
                }
                return key;
        }

        private static byte[] aad(long userId) {
                return ("raillens-mfa:" + userId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        public static class MfaNotConfiguredException extends RuntimeException {
                public MfaNotConfiguredException() {
                        super("MFA is not configured on this server (raillens.mfa.encryption-key)");
                }
        }
}
