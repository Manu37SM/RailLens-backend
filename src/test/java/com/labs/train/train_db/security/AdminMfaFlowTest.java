package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Admin accounts with TOTP MFA, end to end over HTTP on real PostgreSQL, with the legacy shared key switched off. */
@SpringBootTest(properties = {
                "raillens.admin.api-key=legacy-key-that-must-not-work",
                "raillens.admin.legacy-key-enabled=false",
                "raillens.mfa.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@AutoConfigureMockMvc
class AdminMfaFlowTest {

        @Autowired
        private MockMvc mvc;

        @Autowired
        private JdbcClient jdbc;

        @Autowired
        private com.labs.train.train_db.repository.UserRepository users;

        private final ObjectMapper json = new ObjectMapper();
        private final String clientIp = "198.51.100." + ThreadLocalRandom.current().nextInt(1, 250);

        private MockHttpServletResponse call(MockHttpServletRequestBuilder r) throws Exception {
                return mvc.perform(r.with(req -> {
                        req.setRemoteAddr("10.0.0.1");
                        return req;
                }).header("X-Forwarded-For", clientIp)).andReturn().getResponse();
        }

        private MockHttpServletResponse postJson(String path, String body) throws Exception {
                return call(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
        }

        private JsonNode body(MockHttpServletResponse r) throws Exception {
                return json.readTree(r.getContentAsString());
        }

        private String newAdmin() throws Exception {
                String name = "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                var r = postJson("/api/v1/auth/register",
                                "{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"passw0rd-long\"}".formatted(name, name));
                assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
                jdbc.sql("UPDATE users SET role = 'ADMIN' WHERE username = :u").param("u", name).update();
                return name;
        }

        private MockHttpServletResponse login(String name, String otp) throws Exception {
                String otpField = otp == null ? "" : ",\"otp\":\"" + otp + "\"";
                return postJson("/api/v1/auth/login",
                                "{\"usernameOrEmail\":\"%s\",\"password\":\"passw0rd-long\"%s}".formatted(name, otpField));
        }

        private static byte[] decodeBase32(String s) {
                String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
                byte[] out = new byte[s.length() * 5 / 8];
                int buffer = 0, bits = 0, i = 0;
                for (char c : s.toCharArray()) {
                        buffer = (buffer << 5) | alphabet.indexOf(c);
                        bits += 5;
                        if (bits >= 8) {
                                out[i++] = (byte) (buffer >> (bits - 8));
                                bits -= 8;
                        }
                }
                return out;
        }

        /** Signs an admin in through enrolment; returns [secret, accessToken, refreshToken]. */
        private Object[] enrol(String name) throws Exception {
                var first = login(name, null);
                assertThat(first.getStatus()).isEqualTo(200);
                assertThat(body(first).get("mfaEnrolmentRequired").asBoolean()).isTrue();
                assertThat(body(first).get("token").isNull()).isTrue();
                String enrolmentToken = body(first).get("enrolmentToken").asText();

                var started = postJson("/api/v1/auth/mfa/enrol", "{\"enrolmentToken\":\"%s\"}".formatted(enrolmentToken));
                assertThat(started.getStatus()).as(started.getContentAsString()).isEqualTo(200);
                assertThat(body(started).get("otpauthUri").asText()).startsWith("otpauth://totp/RailLens%3A" + name);
                byte[] secret = decodeBase32(body(started).get("secret").asText());

                String code = Totp.code(secret, Totp.step(Instant.now()), 6);
                var confirmed = postJson("/api/v1/auth/mfa/confirm",
                                "{\"enrolmentToken\":\"%s\",\"otp\":\"%s\"}".formatted(enrolmentToken, code));
                assertThat(confirmed.getStatus()).as(confirmed.getContentAsString()).isEqualTo(200);
                assertThat(body(confirmed).get("role").asText()).isEqualTo("ADMIN");
                assertThat(body(confirmed).get("expiresInSeconds").asLong()).isEqualTo(15 * 60);
                return new Object[] {secret, body(confirmed).get("token").asText(), body(confirmed).get("refreshToken").asText()};
        }

        private long lastStep(String name) {
                return jdbc.sql("SELECT mfa_last_step FROM users WHERE username = :u").param("u", name).query(Long.class).single();
        }

        @Test
        void enrolmentThenAdminTokenOpensAdminRoutesAndTheSecretIsStoredEncrypted() throws Exception {
                String name = newAdmin();
                Object[] s = enrol(name);

                var stats = call(get("/api/v1/admin/stats").header("Authorization", "Bearer " + s[1]));
                assertThat(stats.getStatus()).as(stats.getContentAsString()).isEqualTo(200);

                var me = call(get("/api/v1/auth/me").header("Authorization", "Bearer " + s[1]));
                assertThat(body(me).get("role").asText()).isEqualTo("ADMIN");
                assertThat(body(me).get("mfaEnabled").asBoolean()).isTrue();

                byte[] stored = jdbc.sql("SELECT mfa_secret FROM users WHERE username = :u").param("u", name)
                                .query(byte[].class).single();
                assertThat(stored).hasSize(12 + 20 + 16);   // IV + secret + GCM tag: not the raw 20-byte secret
                assertThat(stored).isNotEqualTo((byte[]) s[0]);
        }

        @Test
        void afterEnrolmentAPasswordAloneIsNotEnoughAndEachCodeWorksOnce() throws Exception {
                String name = newAdmin();
                byte[] secret = (byte[]) enrol(name)[0];

                var noCode = login(name, null);
                assertThat(noCode.getStatus()).isEqualTo(401);
                assertThat(body(noCode).get("code").asText()).isEqualTo("MFA_REQUIRED");

                String usedAtEnrolment = Totp.code(secret, lastStep(name), 6);
                var replay = login(name, usedAtEnrolment);
                assertThat(replay.getStatus()).isEqualTo(401);
                assertThat(body(replay).get("code").asText()).isEqualTo("INVALID_OTP");

                String next = Totp.code(secret, lastStep(name) + 1, 6);
                var ok = login(name, next);
                assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
                assertThat(body(ok).get("role").asText()).isEqualTo("ADMIN");

                var again = login(name, next);
                assertThat(again.getStatus()).isEqualTo(401);
                assertThat(body(again).get("code").asText()).isEqualTo("INVALID_OTP");
                // the wrong codes counted toward lockout like wrong passwords
                assertThat(jdbc.sql("SELECT failed_login_attempts FROM users WHERE username = :u").param("u", name)
                                .query(Integer.class).single()).isEqualTo(1);
        }

        @Test
        void refreshKeepsAdminRightsUntilTheAccountIsDemoted() throws Exception {
                String name = newAdmin();
                String refresh = (String) enrol(name)[2];

                var r1 = postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(refresh));
                assertThat(body(r1).get("role").asText()).isEqualTo("ADMIN");
                var stillAdmin = call(get("/api/v1/admin/stats").header("Authorization", "Bearer " + body(r1).get("token").asText()));
                assertThat(stillAdmin.getStatus()).isEqualTo(200);

                jdbc.sql("UPDATE users SET role = 'USER' WHERE username = :u").param("u", name).update();
                var r2 = postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(body(r1).get("refreshToken").asText()));
                assertThat(body(r2).get("role").asText()).isEqualTo("USER");
                var demoted = call(get("/api/v1/admin/stats").header("Authorization", "Bearer " + body(r2).get("token").asText()));
                assertThat(demoted.getStatus()).isEqualTo(401);
                assertThat(body(demoted).get("error").asText()).isEqualTo("Admin sign-in required");
        }

        @Test
        void aPlainUserTokenAndTheEnrolmentTokenNeverGrantAdmin() throws Exception {
                String name = newAdmin();
                String enrolmentToken = body(login(name, null)).get("enrolmentToken").asText();

                var asBearer = call(get("/api/v1/auth/me").header("Authorization", "Bearer " + enrolmentToken));
                assertThat(asBearer.getStatus()).isEqualTo(401);
                var onAdmin = call(get("/api/v1/admin/stats").header("Authorization", "Bearer " + enrolmentToken));
                assertThat(onAdmin.getStatus()).isEqualTo(401);

                String user = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                var reg = postJson("/api/v1/auth/register",
                                "{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"passw0rd-long\"}".formatted(user, user));
                assertThat(body(reg).get("role").asText()).isEqualTo("USER");
                var userOnAdmin = call(get("/api/v1/admin/stats").header("Authorization", "Bearer " + body(reg).get("token").asText()));
                assertThat(userOnAdmin.getStatus()).isEqualTo(401);
        }

        @Test
        void theLegacySharedKeyIsRefusedWhenSwitchedOff() throws Exception {
                var r = call(get("/api/v1/admin/stats").header("X-Admin-Key", "legacy-key-that-must-not-work"));
                assertThat(r.getStatus()).isEqualTo(401);
                assertThat(body(r).get("error").asText()).isEqualTo("X-Admin-Key is no longer accepted - sign in with an admin account");
        }

        @Test
        void enrolmentCannotBeRestartedOnceMfaIsOn() throws Exception {
                String name = newAdmin();
                String enrolmentToken = body(login(name, null)).get("enrolmentToken").asText();
                assertThat(postJson("/api/v1/auth/mfa/enrol", "{\"enrolmentToken\":\"%s\"}".formatted(enrolmentToken)).getStatus())
                                .isEqualTo(200);
                jdbc.sql("UPDATE users SET mfa_enabled = TRUE WHERE username = :u").param("u", name).update();
                var r = postJson("/api/v1/auth/mfa/enrol", "{\"enrolmentToken\":\"%s\"}".formatted(enrolmentToken));
                assertThat(r.getStatus()).isEqualTo(409);
                assertThat(body(r).get("code").asText()).isEqualTo("MFA_ALREADY_ENABLED");
        }

        @Test
        void aUserPromotedWhileSignedInDoesNotGainAdminByRefreshing() throws Exception {
                String name = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                var reg = postJson("/api/v1/auth/register",
                                "{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"passw0rd-long\"}".formatted(name, name));
                jdbc.sql("UPDATE users SET role = 'ADMIN' WHERE username = :u").param("u", name).update();

                var refreshed = postJson("/api/v1/auth/refresh",
                                "{\"refreshToken\":\"%s\"}".formatted(body(reg).get("refreshToken").asText()));
                assertThat(refreshed.getStatus()).isEqualTo(200);
                var r = call(get("/api/v1/admin/stats").header("Authorization", "Bearer " + body(refreshed).get("token").asText()));
                assertThat(r.getStatus()).isEqualTo(401);   // password-only session: admin needs a fresh MFA sign-in
        }

        @Test
        void twoLoginsRacingWithTheSameCodeCannotBothClaimIt() {
                // Totp.verify already refuses a step at or before the stored one; this is the guard for two requests
                // that both read the row before either wrote it.
                String name = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                var u = new com.labs.train.train_db.entity.User();
                u.setUsername(name);
                u.setEmail(name + "@example.com");
                u.setPasswordHash("x");
                Long id = users.save(u).getId();

                assertThat(users.claimMfaStep(id, 1000)).isEqualTo(1);
                assertThat(users.claimMfaStep(id, 1000)).isZero();
                assertThat(users.claimMfaStep(id, 999)).isZero();
                assertThat(users.claimMfaStep(id, 1001)).isEqualTo(1);
        }

        @Test
        void keyIsTheDocumentedFormat() {
                assertThat(Base64.getDecoder().decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")).hasSize(32);
        }
}
