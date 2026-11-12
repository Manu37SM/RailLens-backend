package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Spring Security chain end to end, focused on keeping clients working: same credentials, same error JSON and
 * messages (the apps display them), CORS preflight for the web app, public reads unchanged.
 */
@SpringBootTest(properties = {
                "raillens.admin.api-key=test-admin-key-for-security-chain",
                "raillens.cors.allowed-origins=https://raillens.example"})
@AutoConfigureMockMvc
class SecurityChainTest {

        @Autowired
        private MockMvc mvc;

        private final ObjectMapper json = new ObjectMapper();

        /** Each test is its own client (auth endpoints are rate limited to 10/min per client IP). */
        private final String clientIp = "203.0.113." + ThreadLocalRandom.current().nextInt(1, 250);

        private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r) {
                return r.with(req -> {
                        req.setRemoteAddr("10.0.0.1");   // Render's proxy
                        return req;
                }).header("X-Forwarded-For", clientIp);
        }

        private MockHttpServletResponse call(MockHttpServletRequestBuilder r) throws Exception {
                return mvc.perform(as(r)).andReturn().getResponse();
        }

        private JsonNode body(MockHttpServletResponse r) throws Exception {
                return json.readTree(r.getContentAsString());
        }

        private String register() throws Exception {
                String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                var r = call(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                                "{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"passw0rd-long\"}"
                                                .formatted(name, name)));
                assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
                return body(r).get("token").asText();
        }

        @Test
        void aValidTokenReachesTheAccountEndpointsAsBefore() throws Exception {
                String token = register();
                var me = call(get("/api/v1/auth/me").header("Authorization", "Bearer " + token));
                assertThat(me.getStatus()).isEqualTo(200);
                assertThat(body(me).get("username").asText()).startsWith("u");
        }

        @Test
        void unauthenticatedErrorsKeepTheShapeAndMessagesTheAppsDisplay() throws Exception {
                var missing = call(get("/api/v1/auth/me"));
                assertThat(missing.getStatus()).isEqualTo(401);
                assertThat(body(missing).get("error").asText()).isEqualTo("Missing or invalid Authorization header");
                assertThat(body(missing).has("timestamp")).isTrue();
                assertThat(body(missing).get("status").asInt()).isEqualTo(401);

                var bad = call(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.jwt"));
                assertThat(bad.getStatus()).isEqualTo(401);
                assertThat(body(bad).get("error").asText()).isEqualTo("Invalid or expired token");

                var admin = call(post("/api/v1/admin/cache/clear"));
                assertThat(admin.getStatus()).isEqualTo(401);
                assertThat(body(admin).get("error").asText()).isEqualTo("Missing or invalid X-Admin-Key header");
        }

        @Test
        void aUserTokenIsNotAnAdminCredential() throws Exception {
                String token = register();
                var r = call(post("/api/v1/admin/cache/clear").header("Authorization", "Bearer " + token));
                assertThat(r.getStatus()).isEqualTo(401);
                assertThat(body(r).get("error").asText()).isEqualTo("Missing or invalid X-Admin-Key header");
        }

        @Test
        void aWrongAdminKeyIsRejectedEvenOnAPublicRead() throws Exception {
                var r = call(get("/api/v1/trains").header("X-Admin-Key", "typo"));
                assertThat(r.getStatus()).as("a wrong key never silently falls back to anonymous").isEqualTo(401);
        }

        @Test
        void publicReadsIgnoreAGarbageTokenExactlyAsBefore() throws Exception {
                var r = call(get("/api/v1/trains").header("Authorization", "Bearer garbage"));
                assertThat(r.getStatus()).isEqualTo(200);
        }

        @Test
        void unlistedRoutesAreDeniedByDefault() throws Exception {
                assertThat(call(get("/api/v1/not-a-route")).getStatus()).isEqualTo(401);
                assertThat(call(get("/internal/anything")).getStatus()).isEqualTo(401);
                assertThat(call(delete("/api/v1/stats")).getStatus()).isEqualTo(401);
        }

        @Test
        void browserPreflightFromTheWebAppStillWorks() throws Exception {
                var ok = call(options("/api/v1/auth/login").header("Origin", "https://raillens.example")
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "content-type,authorization"));
                assertThat(ok.getStatus()).isEqualTo(200);
                assertThat(ok.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://raillens.example");

                var foreign = call(options("/api/v1/auth/login").header("Origin", "https://evil.example")
                                .header("Access-Control-Request-Method", "POST"));
                assertThat(foreign.getStatus()).isEqualTo(403);
                assertThat(foreign.getHeader("Access-Control-Allow-Origin")).isNull();
        }

        @Test
        void securityHeadersComeFromOneSourceAndPublicReadsStayCacheable() throws Exception {
                var r = call(get("/api/v1/trains"));
                assertThat(r.getHeaders("X-Content-Type-Options")).containsExactly("nosniff");
                assertThat(r.getHeaders("Strict-Transport-Security")).hasSize(1);
                assertThat(r.getHeader("Cache-Control")).as("Spring Security's no-cache default would defeat caching")
                                .isNull();
        }

        @Test
        void healthStaysOpenForTheRenderHealthCheck() throws Exception {
                assertThat(call(get("/actuator/health")).getStatus()).isEqualTo(200);
        }
}
