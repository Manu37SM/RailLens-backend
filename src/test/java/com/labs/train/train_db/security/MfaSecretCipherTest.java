package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class MfaSecretCipherTest {

        private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
        private final MfaSecretCipher cipher = new MfaSecretCipher(KEY);

        @Test
        void roundTripsAndUsesAFreshIvEachTime() {
                byte[] secret = Totp.newSecret();
                byte[] a = cipher.encrypt(secret, 7);
                byte[] b = cipher.encrypt(secret, 7);
                assertThat(a).isNotEqualTo(b);
                assertThat(cipher.decrypt(a, 7)).isEqualTo(secret);
        }

        @Test
        void aCiphertextMovedToAnotherUsersRowDoesNotDecrypt() {
                byte[] stored = cipher.encrypt(Totp.newSecret(), 7);
                assertThatThrownBy(() -> cipher.decrypt(stored, 8)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void aTamperedCiphertextDoesNotDecrypt() {
                byte[] stored = cipher.encrypt(Totp.newSecret(), 7);
                stored[stored.length - 1] ^= 1;
                assertThatThrownBy(() -> cipher.decrypt(stored, 7)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejectsAKeyOfTheWrongLengthAtStartup() {
                String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
                assertThatThrownBy(() -> new MfaSecretCipher(shortKey)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void withoutAKeyTheAppRunsButMfaOperationsFailClearly() {
                MfaSecretCipher none = new MfaSecretCipher("");
                assertThat(none.isConfigured()).isFalse();
                assertThatThrownBy(() -> none.encrypt(new byte[20], 1))
                                .isInstanceOf(MfaSecretCipher.MfaNotConfiguredException.class);
        }
}
