package com.labs.train.train_db.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 time-based one-time passwords: HMAC-SHA1, 30-second steps, 6 digits. These are the defaults every
 * authenticator app (Google Authenticator, Microsoft Authenticator, Authy, 1Password...) uses when given an
 * otpauth:// URI without overrides. Tested against the RFC's own vectors.
 */
public final class Totp {

        public static final int DIGITS = 6;
        public static final long STEP_SECONDS = 30;
        /** Accept the previous and next step too: tolerates ~30 s of clock drift between phone and server. */
        public static final int WINDOW = 1;

        private static final SecureRandom RANDOM = new SecureRandom();
        private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        private Totp() {
        }

        /** 160-bit secret, the RFC 4226 recommended length. */
        public static byte[] newSecret() {
                byte[] secret = new byte[20];
                RANDOM.nextBytes(secret);
                return secret;
        }

        public static long step(Instant now) {
                return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
        }

        public static String code(byte[] secret, long step, int digits) {
                try {
                        Mac mac = Mac.getInstance("HmacSHA1");
                        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
                        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
                        int offset = hash[hash.length - 1] & 0x0f;
                        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                                        | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
                        int modulus = (int) Math.pow(10, digits);
                        return String.format("%0" + digits + "d", binary % modulus);
                } catch (GeneralSecurityException e) {
                        throw new IllegalStateException("HmacSHA1 unavailable", e);
                }
        }

        /**
         * The time step the code matches, within +/- {@link #WINDOW} steps of now, and strictly after
         * {@code lastUsedStep} (each code works once). Empty if it matches nothing. Constant-time comparisons.
         */
        public static OptionalLong verify(byte[] secret, String code, Instant now, Long lastUsedStep) {
                if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
                        return OptionalLong.empty();
                }
                long current = step(now);
                byte[] given = code.getBytes(StandardCharsets.US_ASCII);
                OptionalLong match = OptionalLong.empty();
                for (long s = current - WINDOW; s <= current + WINDOW; s++) {
                        boolean equal = MessageDigest.isEqual(given,
                                        code(secret, s, DIGITS).getBytes(StandardCharsets.US_ASCII));
                        if (equal && match.isEmpty() && (lastUsedStep == null || s > lastUsedStep)) {
                                match = OptionalLong.of(s);   // keep looping: same work whatever matched
                        }
                }
                return match;
        }

        public static String base32(byte[] data) {
                StringBuilder out = new StringBuilder();
                int buffer = 0;
                int bits = 0;
                for (byte b : data) {
                        buffer = (buffer << 8) | (b & 0xff);
                        bits += 8;
                        while (bits >= 5) {
                                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                                bits -= 5;
                        }
                }
                if (bits > 0) {
                        out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
                }
                return out.toString();
        }

        /** otpauth URI that authenticator apps import (also what a QR code encodes). */
        public static String otpauthUri(String issuer, String account, byte[] secret) {
                String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
                return "otpauth://totp/" + label + "?secret=" + base32(secret)
                                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
                                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
        }
}
