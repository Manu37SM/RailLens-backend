package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

        private JwtService jwtService;

        @BeforeEach
        void setUp() {
                jwtService = new JwtService();
                ReflectionTestUtils.setField(jwtService, "configuredSecret", "a-test-secret-that-is-at-least-32-bytes-long");
                ReflectionTestUtils.setField(jwtService, "expirationMinutes", 60L);
                ReflectionTestUtils.invokeMethod(jwtService, "init");
        }

        @Test
        void generatesATokenThatValidatesBackToTheSameUsername() {
                String token = jwtService.generateToken("manish");

                String username = jwtService.validateAndGetUsername(token);

                assertThat(username).isEqualTo("manish");
        }

        @Test
        void rejectsAMalformedToken() {
                String username = jwtService.validateAndGetUsername("not-a-real-jwt");

                assertThat(username).isNull();
        }

        @Test
        void rejectsATokenSignedWithADifferentSecret() {
                JwtService otherService = new JwtService();
                ReflectionTestUtils.setField(otherService, "configuredSecret", "a-different-test-secret-thats-also-32-bytes");
                ReflectionTestUtils.setField(otherService, "expirationMinutes", 60L);
                ReflectionTestUtils.invokeMethod(otherService, "init");

                String tokenFromOtherService = otherService.generateToken("manish");

                assertThat(jwtService.validateAndGetUsername(tokenFromOtherService)).isNull();
        }

        @Test
        void refusesToStartWithoutAConfiguredSecret() {
                JwtService unconfigured = new JwtService();
                ReflectionTestUtils.setField(unconfigured, "configuredSecret", "");

                assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(unconfigured, "init"))
                                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void refusesToStartWithASecretShorterThan32Bytes() {
                JwtService weak = new JwtService();
                ReflectionTestUtils.setField(weak, "configuredSecret", "too-short");

                assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(weak, "init"))
                                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void exposesExpirationInSecondsDerivedFromConfiguredMinutes() {
                assertThat(jwtService.getExpirationSeconds()).isEqualTo(60L * 60L);
        }
}
