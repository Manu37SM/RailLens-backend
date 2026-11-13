package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.labs.train.train_db.security.RailOpsTestIssuer.Claims;

import tools.jackson.databind.ObjectMapper;

/**
 * Phase 4D: a linked profile's password lives in RailOps. Changing it or closing the account is forwarded to RailOps
 * with the user's own token, and RailLens' local copy can't be used afterwards. Real PostgreSQL, fake RailOps.
 */
@SpringBootTest(properties = "raillens.mfa.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@AutoConfigureMockMvc
class RailOpsAccountChangesTest {

        static final String LOCAL_PASSWORD = "passw0rd-long";

        @DynamicPropertySource
        static void railOps(DynamicPropertyRegistry r) {
                RailOpsTestIssuer.register(r);
        }

        @Autowired
        MockMvc mvc;

        @Autowired
        JdbcClient jdbc;

        final ObjectMapper json = new ObjectMapper();
        final String clientIp = "203.0.113." + ThreadLocalRandom.current().nextInt(1, 250);

        record Linked(String username, String email, UUID sub, String railOpsToken, String localToken) {
        }

        @BeforeEach
        void clearCalls() {
                RailOpsTestIssuer.ACCOUNT_CALLS.clear();
        }

        MockHttpServletResponse call(MockHttpServletRequestBuilder r) throws Exception {
                return mvc.perform(r.with(req -> {
                        req.setRemoteAddr("10.0.0.1");
                        return req;
                }).header("X-Forwarded-For", clientIp)).andReturn().getResponse();
        }

        /** A RailLens account (with its own password) that the Phase 4C move linked to a RailOps login. */
        Linked linked() throws Exception {
                String name = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                String email = name + "@example.com";
                var reg = call(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, LOCAL_PASSWORD)));
                assertThat(reg.getStatus()).isEqualTo(200);
                UUID sub = UUID.randomUUID();
                jdbc.sql("UPDATE users SET railops_user_id = :s WHERE username = :u").param("s", sub).param("u", name).update();
                return new Linked(name, email, sub, RailOpsTestIssuer.token(Claims.passenger(sub, email)),
                                json.readTree(reg.getContentAsString()).get("token").asString());
        }

        MockHttpServletResponse changePassword(String token, String current, String next) throws Exception {
                return call(put("/api/v1/auth/password").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
        }

        MockHttpServletResponse deleteAccount(String token, String password) throws Exception {
                return call(delete("/api/v1/auth/me").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"%s\"}".formatted(password)));
        }

        int localLogin(String username, String password) throws Exception {
                return call(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(username, password))).getStatus();
        }

        String localHash(String username) {
                return jdbc.sql("SELECT password_hash FROM users WHERE username = :u").param("u", username).query(String.class).optional().orElse(null);
        }

        int profiles(String username) {
                return jdbc.sql("SELECT count(*) FROM users WHERE username = :u").param("u", username).query(Integer.class).single();
        }

        @Test
        void password_change_is_forwarded_to_railops_and_the_old_local_password_stops_working() throws Exception {
                Linked u = linked();
                var r = changePassword(u.railOpsToken(), RailOpsTestIssuer.RIGHT_PASSWORD, "brand-new-pass-9");
                assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(204);

                assertThat(RailOpsTestIssuer.ACCOUNT_CALLS).singleElement().satisfies(c -> {
                        assertThat(c).startsWith("/api/v1/auth/password|Bearer " + u.railOpsToken() + "|");
                        assertThat(c).contains("brand-new-pass-9");
                });
                assertThat(localHash(u.username())).isNull();
                assertThat(localLogin(u.username(), LOCAL_PASSWORD)).isEqualTo(401);
        }

        @Test
        void a_refused_password_change_leaves_the_local_password_in_place() throws Exception {
                Linked u = linked();
                String before = localHash(u.username());

                assertThat(changePassword(u.railOpsToken(), "wrong-password-1", "brand-new-pass-9").getStatus()).isEqualTo(401);
                assertThat(changePassword(u.railOpsToken(), RailOpsTestIssuer.RIGHT_PASSWORD, "weak-1").getStatus()).isEqualTo(400);

                assertThat(localHash(u.username())).isEqualTo(before);
                assertThat(localLogin(u.username(), LOCAL_PASSWORD)).isEqualTo(200);
        }

        @Test
        void closing_the_account_erases_the_railops_login_too() throws Exception {
                Linked u = linked();
                assertThat(deleteAccount(u.railOpsToken(), "wrong-password-1").getStatus()).isEqualTo(401);
                assertThat(profiles(u.username())).isEqualTo(1);

                var r = deleteAccount(u.railOpsToken(), RailOpsTestIssuer.RIGHT_PASSWORD);
                assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(204);
                assertThat(profiles(u.username())).isZero();
                assertThat(RailOpsTestIssuer.ACCOUNT_CALLS).last().asString().startsWith("/api/v1/auth/erase|Bearer " + u.railOpsToken() + "|");
        }

        @Test
        void a_linked_profile_signed_in_with_a_raillens_token_is_sent_to_railops() throws Exception {
                Linked u = linked();
                var change = changePassword(u.localToken(), LOCAL_PASSWORD, "brand-new-pass-9");
                assertThat(change.getStatus()).isEqualTo(409);
                assertThat(change.getContentAsString()).contains("MANAGED_BY_RAILOPS");
                assertThat(deleteAccount(u.localToken(), LOCAL_PASSWORD).getStatus()).isEqualTo(409);
                assertThat(profiles(u.username())).isEqualTo(1);
                assertThat(RailOpsTestIssuer.ACCOUNT_CALLS).isEmpty();
        }

        @Test
        void unlinked_accounts_keep_working_exactly_as_before() throws Exception {
                String name = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                var reg = call(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"%s\"}".formatted(name, name, LOCAL_PASSWORD)));
                String token = json.readTree(reg.getContentAsString()).get("token").asString();
                assertThat(changePassword(token, LOCAL_PASSWORD, "brand-new-pass-9").getStatus()).isEqualTo(204);
                assertThat(localLogin(name, "brand-new-pass-9")).isEqualTo(200);
                assertThat(RailOpsTestIssuer.ACCOUNT_CALLS).isEmpty();
        }

        @Test
        void railops_errors_are_mapped_to_messages_the_website_can_show() {
                assertThat(RailOpsAccountClient.mapped(500).getStatus().value()).isEqualTo(503);
                assertThat(RailOpsAccountClient.mapped(429).getStatus().value()).isEqualTo(429);
                assertThat(RailOpsAccountClient.mapped(403).getStatus().value()).isEqualTo(403);
        }
}
