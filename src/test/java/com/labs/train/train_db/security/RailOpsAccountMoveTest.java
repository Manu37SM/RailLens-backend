package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Phase 4C, RailLens side: export of accounts to move (admin + MFA only) and storing the links. */
@SpringBootTest(properties = {
                "raillens.mfa.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
                "raillens.admin.api-key=legacy-shared-key-for-tests"})
@AutoConfigureMockMvc
class RailOpsAccountMoveTest {

        @DynamicPropertySource
        static void railOps(DynamicPropertyRegistry r) {
                RailOpsTestIssuer.register(r);
        }

        @Autowired
        MockMvc mvc;

        @Autowired
        JdbcClient jdbc;

        final ObjectMapper json = new ObjectMapper();
        final String clientIp = "198.51.100." + ThreadLocalRandom.current().nextInt(1, 250);
        final String adminToken = RailOpsTestIssuer.token(Claims.globalAdmin(UUID.randomUUID(), "ops-" + UUID.randomUUID() + "@example.com"));

        MockHttpServletResponse call(MockHttpServletRequestBuilder r) throws Exception {
                return mvc.perform(r.with(req -> {
                        req.setRemoteAddr("10.0.0.1");
                        return req;
                }).header("X-Forwarded-For", clientIp)).andReturn().getResponse();
        }

        long register() throws Exception {
                String name = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                var r = call(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"passw0rd-long\"}".formatted(name, name)));
                assertThat(r.getStatus()).isEqualTo(200);
                return jdbc.sql("SELECT id FROM users WHERE username = :u").param("u", name).query(Long.class).single();
        }

        JsonNode pendingAfter(long afterId) throws Exception {
                var r = call(get("/api/v1/admin/railops/accounts").param("afterId", String.valueOf(afterId)).param("limit", "500")
                                .header("Authorization", "Bearer " + adminToken));
                assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
                assertThat(r.getHeader("Cache-Control")).contains("no-store");
                return json.readTree(r.getContentAsString());
        }

        @Test
        void exports_unlinked_accounts_with_their_password_hash_oldest_first() throws Exception {
                long a = register();
                long b = register();
                JsonNode page = pendingAfter(a - 1);
                assertThat(page.get(0).get("raillensUserId").asLong()).isEqualTo(a);
                assertThat(page.get(0).get("passwordHash").asText()).startsWith("$2");   // bcrypt, moved as is
                assertThat(page.get(1).get("raillensUserId").asLong()).isEqualTo(b);
                assertThat(pendingAfter(b).findValues("raillensUserId")).noneMatch(n -> n.asLong() <= b);
        }

        @Test
        void stores_each_link_once_and_never_repoints_or_shares_one() throws Exception {
                long a = register();
                long b = register();
                UUID ra = UUID.randomUUID();
                String body = "{\"links\":[{\"raillensUserId\":%d,\"railopsUserId\":\"%s\"}]}".formatted(a, ra);
                var first = call(post("/api/v1/admin/railops/links").contentType(MediaType.APPLICATION_JSON).content(body)
                                .header("Authorization", "Bearer " + adminToken));
                assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);
                assertThat(json.readTree(first.getContentAsString()).get("linked").asInt()).isEqualTo(1);

                var again = json.readTree(call(post("/api/v1/admin/railops/links").contentType(MediaType.APPLICATION_JSON).content(body)
                                .header("Authorization", "Bearer " + adminToken)).getContentAsString());
                assertThat(again.get("alreadyLinked").asInt()).isEqualTo(1);

                String repoint = "{\"links\":[{\"raillensUserId\":%d,\"railopsUserId\":\"%s\"},{\"raillensUserId\":%d,\"railopsUserId\":\"%s\"}]}"
                                .formatted(a, UUID.randomUUID(), b, ra);
                var bad = json.readTree(call(post("/api/v1/admin/railops/links").contentType(MediaType.APPLICATION_JSON).content(repoint)
                                .header("Authorization", "Bearer " + adminToken)).getContentAsString());
                assertThat(bad.get("conflicts")).hasSize(2);
                assertThat(jdbc.sql("SELECT railops_user_id FROM users WHERE id = :id").param("id", a).query(UUID.class).single()).isEqualTo(ra);
                assertThat(jdbc.sql("SELECT railops_user_id FROM users WHERE id = :id").param("id", b).query(UUID.class).optional()).isEmpty();

                assertThat(pendingAfter(a - 1).findValues("raillensUserId")).as("linked accounts are no longer exported")
                                .noneMatch(n -> n.asLong() == a);
        }

        @Test
        void the_shared_admin_key_and_ordinary_users_cannot_export_password_hashes() throws Exception {
                var legacy = call(get("/api/v1/admin/railops/accounts").header("X-Admin-Key", "legacy-shared-key-for-tests"));
                assertThat(legacy.getStatus()).isEqualTo(403);
                assertThat(legacy.getContentAsString()).contains("MFA_ADMIN_REQUIRED").doesNotContain("$2");

                String passenger = RailOpsTestIssuer.token(Claims.passenger(UUID.randomUUID(), "p-" + UUID.randomUUID() + "@example.com"));
                assertThat(call(get("/api/v1/admin/railops/accounts").header("Authorization", "Bearer " + passenger)).getStatus())
                                .isEqualTo(401);
                assertThat(call(get("/api/v1/admin/railops/accounts")).getStatus()).isEqualTo(401);
        }
}
