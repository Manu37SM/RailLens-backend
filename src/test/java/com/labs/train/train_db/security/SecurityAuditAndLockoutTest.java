package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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

import com.labs.train.train_db.audit.SecurityAuditService;

/**
 * Phase 6 on real PostgreSQL: the security audit chain (append-only, tamper-evident, no emails or passwords) and
 * progressive lockout. Same properties as AdminMfaFlowTest, so the Spring context is shared.
 */
@SpringBootTest(properties = {
                "raillens.admin.api-key=legacy-key-that-must-not-work",
                "raillens.admin.legacy-key-enabled=false",
                "raillens.mfa.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@AutoConfigureMockMvc
class SecurityAuditAndLockoutTest {

        static final String PASSWORD = "passw0rd-long";

        @Autowired
        MockMvc mvc;

        @Autowired
        JdbcClient jdbc;

        @Autowired
        SecurityAuditService audit;

        /** A fresh client IP per request, so the per-IP sign-in rate limit never interferes. */
        MockHttpServletResponse postJson(String path, String body) throws Exception {
                String ip = "198.51.100." + ThreadLocalRandom.current().nextInt(1, 250);
                return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body).with(req -> {
                        req.setRemoteAddr("10.0.0.1");
                        return req;
                }).header("X-Forwarded-For", ip)).andReturn().getResponse();
        }

        String newUser() throws Exception {
                String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                var r = postJson("/api/v1/auth/register",
                                "{\"username\":\"%s\",\"email\":\"%s@secret-mail.example\",\"password\":\"%s\"}".formatted(name, name, PASSWORD));
                assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
                return name;
        }

        int login(String name, String password) throws Exception {
                return postJson("/api/v1/auth/login",
                                "{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(name, password)).getStatus();
        }

        List<String> actionsFor(String name) {
                return jdbc.sql("SELECT action FROM security_audit WHERE actor = :a ORDER BY id").param("a", name)
                                .query(String.class).list();
        }

        @Test
        void sign_ins_are_recorded_in_order_and_the_chain_verifies() throws Exception {
                String name = newUser();
                assertThat(login(name, "wrong-password-1")).isEqualTo(401);
                assertThat(login(name, PASSWORD)).isEqualTo(200);

                assertThat(actionsFor(name)).containsExactly("REGISTERED", "LOGIN_FAILED", "LOGIN_SUCCEEDED");
                assertThat(audit.verify().intact()).isTrue();
        }

        @Test
        void no_email_password_or_typed_identifier_is_ever_written() throws Exception {
                String name = newUser();
                login(name, "wrong-password-1");
                login("someone@secret-mail.example", "another-guess-2");
                String all = String.join("|", jdbc.sql("SELECT concat_ws('|', actor, action, target, detail) FROM security_audit")
                                .query(String.class).list());
                assertThat(all).doesNotContain("secret-mail.example").doesNotContain("wrong-password-1")
                                .doesNotContain("another-guess-2").doesNotContain(PASSWORD);
        }

        @Test
        void rows_cannot_be_changed_or_deleted() throws Exception {
                newUser();
                long id = jdbc.sql("SELECT max(id) FROM security_audit").query(Long.class).single();
                assertThatThrownBy(() -> jdbc.sql("UPDATE security_audit SET detail = 'x' WHERE id = :id").param("id", id).update())
                                .hasMessageContaining("append-only");
                assertThatThrownBy(() -> jdbc.sql("DELETE FROM security_audit WHERE id = :id").param("id", id).update())
                                .hasMessageContaining("append-only");
        }

        @Test
        void a_changed_row_is_found_by_verify() throws Exception {
                newUser();
                long id = jdbc.sql("SELECT max(id) FROM security_audit").query(Long.class).single();
                String original = jdbc.sql("SELECT actor FROM security_audit WHERE id = :id").param("id", id).query(String.class).single();
                // Someone with database owner rights switches the guard off and edits history:
                jdbc.sql("ALTER TABLE security_audit DISABLE TRIGGER security_audit_no_update_delete").update();
                try {
                        jdbc.sql("UPDATE security_audit SET actor = 'someone-else' WHERE id = :id").param("id", id).update();
                        var v = audit.verify();
                        assertThat(v.intact()).isFalse();
                        assertThat(v.firstBrokenId()).isEqualTo(id);
                } finally {
                        jdbc.sql("UPDATE security_audit SET actor = :a WHERE id = :id").param("a", original).param("id", id).update();
                        jdbc.sql("ALTER TABLE security_audit ENABLE TRIGGER security_audit_no_update_delete").update();
                }
                assertThat(audit.verify().intact()).isTrue();
        }

        Duration lockedFor(String name) {
                LocalDateTime until = jdbc.sql("SELECT locked_until FROM users WHERE username = :u").param("u", name)
                                .query(LocalDateTime.class).single();
                return Duration.between(LocalDateTime.now(), until);
        }

        void failFiveTimes(String name) throws Exception {
                for (int i = 0; i < 5; i++) {
                        login(name, "wrong-password-" + i);
                }
        }

        @Test
        void each_lockout_in_a_row_doubles_and_a_good_sign_in_resets_the_streak() throws Exception {
                String name = newUser();
                failFiveTimes(name);
                assertThat(lockedFor(name)).isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15));
                assertThat(login(name, PASSWORD)).as("locked: even the right password").isEqualTo(423);

                jdbc.sql("UPDATE users SET locked_until = now() - interval '1 second' WHERE username = :u").param("u", name).update();
                failFiveTimes(name);
                assertThat(lockedFor(name)).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));

                jdbc.sql("UPDATE users SET locked_until = now() - interval '1 second' WHERE username = :u").param("u", name).update();
                assertThat(login(name, PASSWORD)).isEqualTo(200);
                assertThat(jdbc.sql("SELECT lockout_count FROM users WHERE username = :u").param("u", name).query(Integer.class).single())
                                .isZero();
                failFiveTimes(name);
                assertThat(lockedFor(name)).as("streak reset").isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15));
                assertThat(actionsFor(name)).contains("ACCOUNT_LOCKED", "LOGIN_BLOCKED_LOCKED");
        }

        @Test
        void the_audit_endpoints_are_for_admins_only() throws Exception {
                assertThat(mvc.perform(get("/api/v1/admin/audit/verify")).andReturn().getResponse().getStatus()).isEqualTo(401);
                String name = newUser();
                var r = postJson("/api/v1/auth/login", "{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(name, PASSWORD));
                String token = new tools.jackson.databind.ObjectMapper().readTree(r.getContentAsString()).get("token").asString();
                assertThat(mvc.perform(get("/api/v1/admin/audit").header("Authorization", "Bearer " + token)).andReturn()
                                .getResponse().getStatus()).as("a signed-in non-admin is refused").isIn(401, 403);
        }
}
