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
                ReflectionTestUtils.setField(jwtService, "adminExpirationMinutes", 15L);
                ReflectionTestUtils.setField(jwtService, "enrolmentTokenMinutes", 5L);
                ReflectionTestUtils.invokeMethod(jwtService, "init");
        }

        private static com.labs.train.train_db.entity.User admin() {
                var user = new com.labs.train.train_db.entity.User();
                user.setUsername("ops");
                user.setRole(com.labs.train.train_db.entity.User.ROLE_ADMIN);
                return user;
        }

        @Test
        void anAdminTokenGrantsAdminOnlyWhenTheSessionPassedMfa() {
                assertThat(jwtService.parseAccessToken(jwtService.generateToken(admin(), true)).admin()).isTrue();
                assertThat(jwtService.parseAccessToken(jwtService.generateToken(admin(), false)).admin()).isFalse();
                assertThat(jwtService.getExpirationSeconds(admin(), true)).isEqualTo(15 * 60);
        }

        @Test
        void aRoleClaimWithoutTheMfaClaimDoesNotGrantAdmin() {
                // Defence in depth: only generateToken(user, true) writes both claims, but the filter must not trust
                // a role claim alone (e.g. from a future code path that forgets MFA).
                var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                "a-test-secret-that-is-at-least-32-bytes-long".getBytes());
                String roleOnly = io.jsonwebtoken.Jwts.builder().subject("ops").claim("role", "ADMIN")
                                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
                var claims = jwtService.parseAccessToken(roleOnly);
                assertThat(claims.username()).isEqualTo("ops");
                assertThat(claims.admin()).isFalse();
        }

        @Test
        void anEnrolmentTokenIsNotAnAccessTokenAndViceVersa() {
                String enrolment = jwtService.generateEnrolmentToken("ops");
                assertThat(jwtService.validateEnrolmentToken(enrolment)).isEqualTo("ops");
                assertThat(jwtService.parseAccessToken(enrolment)).isNull();
                assertThat(jwtService.validateAndGetUsername(enrolment)).isNull();
                assertThat(jwtService.validateEnrolmentToken(jwtService.generateToken("ops"))).isNull();
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
