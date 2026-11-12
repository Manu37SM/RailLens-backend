package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/** RFC 6238 Appendix B (SHA1 seed) vectors, truncated to 6 digits, plus the one-use and drift rules. */
class TotpTest {

        private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        @Test
        void matchesTheRfc6238Vectors() {
                // RFC lists 8-digit codes 94287082, 07081804, 14050471, 89005924; the 6-digit codes are the last 6.
                assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)), 8)).isEqualTo("94287082");
                assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)), 6)).isEqualTo("287082");
                assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)), 6)).isEqualTo("081804");
                assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111111)), 6)).isEqualTo("050471");
                assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)), 6)).isEqualTo("005924");
        }

        @Test
        void base32MatchesWhatAuthenticatorAppsExpect() {
                assertThat(Totp.base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
                assertThat(Totp.base32("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
        }

        @Test
        void acceptsOneStepOfClockDriftEitherWayButNotTwo() {
                Instant now = Instant.ofEpochSecond(1_700_000_000L);
                long step = Totp.step(now);
                assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1, 6), now, null)).hasValue(step - 1);
                assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1, 6), now, null)).hasValue(step + 1);
                assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2, 6), now, null)).isEmpty();
                assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2, 6), now, null)).isEmpty();
        }

        @Test
        void aCodeAtOrBeforeTheLastUsedStepIsRefused() {
                Instant now = Instant.ofEpochSecond(1_700_000_000L);
                long step = Totp.step(now);
                String code = Totp.code(RFC_SECRET, step, 6);
                assertThat(Totp.verify(RFC_SECRET, code, now, step)).isEmpty();
                assertThat(Totp.verify(RFC_SECRET, code, now, step - 1)).isEqualTo(OptionalLong.of(step));
        }

        @Test
        void rejectsMalformedInput() {
                Instant now = Instant.ofEpochSecond(59);
                assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEmpty();
                assertThat(Totp.verify(RFC_SECRET, "28708", now, null)).isEmpty();
                assertThat(Totp.verify(RFC_SECRET, "2870821", now, null)).isEmpty();
                assertThat(Totp.verify(RFC_SECRET, "28708a", now, null)).isEmpty();
        }

        @Test
        void otpauthUriCarriesIssuerAccountAndParameters() {
                assertThat(Totp.otpauthUri("RailLens", "ops admin", RFC_SECRET)).isEqualTo(
                                "otpauth://totp/RailLens%3Aops%20admin?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
                                                + "&issuer=RailLens&algorithm=SHA1&digits=6&period=30");
        }
}
