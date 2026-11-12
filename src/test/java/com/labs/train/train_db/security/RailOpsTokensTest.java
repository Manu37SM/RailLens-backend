package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterAll;
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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Phase 4B: RailOps login tokens accepted by RailLens, end to end over HTTP with a real JWKS address and PostgreSQL. */
@SpringBootTest(properties = "raillens.mfa.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@AutoConfigureMockMvc
class RailOpsTokensTest {

        static final String ISSUER = "https://railops-identity.example";
        static final KeyPair KEYS = rsa();
        static final KeyPair OTHER_KEYS = rsa();
        static final HttpServer JWKS = jwksServer();

        @DynamicPropertySource
        static void railOps(DynamicPropertyRegistry r) {
                r.add("raillens.railops.jwks-url", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/.well-known/jwks.json");
                r.add("raillens.railops.issuer", () -> ISSUER);
        }

        @AfterAll
        static void stop() {
                JWKS.stop(0);
        }

        @Autowired
        MockMvc mvc;

        @Autowired
        JdbcClient jdbc;

        final ObjectMapper json = new ObjectMapper();
        final String clientIp = "198.51.100." + ThreadLocalRandom.current().nextInt(1, 250);

        static KeyPair rsa() {
                try {
                        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
                        g.initialize(2048);
                        return g.generateKeyPair();
                } catch (Exception e) {
                        throw new IllegalStateException(e);
                }
        }

        static HttpServer jwksServer() {
                try {
                        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                        byte[] body = new JWKSet(new RSAKey.Builder((RSAPublicKey) KEYS.getPublic()).keyID("railops-1").build())
                                        .toString().getBytes(StandardCharsets.UTF_8);
                        s.createContext("/.well-known/jwks.json", ex -> {
                                ex.getResponseHeaders().add("Content-Type", "application/json");
                                ex.sendResponseHeaders(200, body.length);
                                ex.getResponseBody().write(body);
                                ex.close();
                        });
                        s.start();
                        return s;
                } catch (Exception e) {
                        throw new IllegalStateException(e);
                }
        }

        record Claims(UUID sub, String email, String issuer, String audience, Instant expires, List<String> roles,
                        List<String> grants, List<String> amr, KeyPair keys) {
                static Claims passenger(UUID sub, String email) {
                        return new Claims(sub, email, ISSUER, "railops-api", Instant.now().plusSeconds(600), List.of("PASSENGER"),
                                        List.of(), List.of("pwd"), KEYS);
                }
        }

        static String token(Claims c) {
                try {
                        var claims = new JWTClaimsSet.Builder().issuer(c.issuer()).subject(c.sub().toString()).audience(c.audience())
                                        .issueTime(new Date()).expirationTime(Date.from(c.expires()))
                                        .claim("utyp", "PASSENGER").claim("roles", c.roles()).claim("grants", c.grants()).claim("amr", c.amr());
                        if (c.email() != null) {
                                claims.claim("email", c.email());
                        }
                        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("railops-1").build(), claims.build());
                        jwt.sign(new RSASSASigner((RSAPrivateKey) c.keys().getPrivate()));
                        return jwt.serialize();
                } catch (Exception e) {
                        throw new IllegalStateException(e);
                }
        }

        MockHttpServletResponse call(MockHttpServletRequestBuilder r) throws Exception {
                return mvc.perform(r.with(req -> {
                        req.setRemoteAddr("10.0.0.1");
                        return req;
                }).header("X-Forwarded-For", clientIp)).andReturn().getResponse();
        }

        MockHttpServletResponse me(String token) throws Exception {
                return call(get("/api/v1/auth/me").header("Authorization", "Bearer " + token));
        }

        JsonNode body(MockHttpServletResponse r) throws Exception {
                return json.readTree(r.getContentAsString());
        }

        static String email() {
                return "ro." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        }

        @Test
        void a_new_railops_user_gets_a_raillens_profile_once() throws Exception {
                UUID sub = UUID.randomUUID();
                String email = email();
                var first = me(token(Claims.passenger(sub, email)));
                assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);
                assertThat(body(first).get("email").asText()).isEqualTo(email);
                String username = body(first).get("username").asText();
                assertThat(username).matches("ro_[a-z0-9_]+_[0-9a-f]{6}");

                assertThat(body(me(token(Claims.passenger(sub, email)))).get("username").asText()).isEqualTo(username);
                assertThat(jdbc.sql("SELECT count(*) FROM users WHERE railops_user_id = :s").param("s", sub).query(Integer.class).single())
                                .isEqualTo(1);
                assertThat(jdbc.sql("SELECT password_hash FROM users WHERE railops_user_id = :s").param("s", sub)
                                .query(String.class).optional()).as("no RailLens password").isEmpty();
        }

        @Test
        void an_existing_account_is_reached_only_through_its_link_never_by_email() throws Exception {
                String name = "l" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                String email = name + "@example.com";
                var reg = call(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"passw0rd-long\"}".formatted(name, email)));
                assertThat(reg.getStatus()).isEqualTo(200);

                // Someone registered the same email at RailOps: must NOT open this account.
                var stranger = me(token(Claims.passenger(UUID.randomUUID(), email.toUpperCase())));
                assertThat(stranger.getStatus()).isEqualTo(401);
                assertThat(body(stranger).get("error").asText()).contains("isn't linked");
                assertThat(jdbc.sql("SELECT railops_user_id FROM users WHERE username = :u").param("u", name)
                                .query(UUID.class).optional()).isEmpty();

                // After the account move (4C) links it, the owner's RailOps login opens it.
                UUID owner = UUID.randomUUID();
                jdbc.sql("UPDATE users SET railops_user_id = :s WHERE username = :u").param("s", owner).param("u", name).update();
                var linked = me(token(Claims.passenger(owner, email)));
                assertThat(linked.getStatus()).isEqualTo(200);
                assertThat(body(linked).get("username").asText()).isEqualTo(name);
        }

        @Test
        void a_differently_capitalised_email_is_still_the_same_person() throws Exception {
                String name = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                String mixedCase = "Asha." + name + "@Example.com";
                assertThat(call(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"passw0rd-long\"}".formatted(name, mixedCase)))
                                .getStatus()).isEqualTo(200);
                UUID sub = UUID.randomUUID();
                assertThat(me(token(Claims.passenger(sub, mixedCase.toLowerCase()))).getStatus()).isEqualTo(401);
                assertThat(jdbc.sql("SELECT count(*) FROM users WHERE lower(email) = :e").param("e", mixedCase.toLowerCase())
                                .query(Integer.class).single()).as("no second profile for the same person").isEqualTo(1);
        }

        @Test
        void forged_expired_or_misaddressed_tokens_are_refused() throws Exception {
                UUID sub = UUID.randomUUID();
                Claims ok = Claims.passenger(sub, email());
                assertThat(me(token(new Claims(sub, ok.email(), ISSUER, "railops-api", ok.expires(), ok.roles(), ok.grants(), ok.amr(), OTHER_KEYS)))
                                .getStatus()).as("signed by another key").isEqualTo(401);
                assertThat(me(token(new Claims(sub, ok.email(), "https://evil.example", "railops-api", ok.expires(), ok.roles(), ok.grants(), ok.amr(), KEYS)))
                                .getStatus()).as("wrong issuer").isEqualTo(401);
                assertThat(me(token(new Claims(sub, ok.email(), ISSUER, "railops-mfa", ok.expires(), ok.roles(), ok.grants(), ok.amr(), KEYS)))
                                .getStatus()).as("enrolment-only audience").isEqualTo(401);
                assertThat(me(token(new Claims(sub, ok.email(), ISSUER, "railops-api", Instant.now().minusSeconds(120), ok.roles(), ok.grants(), ok.amr(), KEYS)))
                                .getStatus()).as("expired").isEqualTo(401);
                assertThat(me(token(new Claims(sub, null, ISSUER, "railops-api", ok.expires(), ok.roles(), ok.grants(), ok.amr(), KEYS)))
                                .getStatus()).as("no email claim").isEqualTo(401);
                assertThat(jdbc.sql("SELECT count(*) FROM users WHERE railops_user_id = :s").param("s", sub).query(Integer.class).single())
                                .as("nothing created from a refused token").isZero();
        }

        @Test
        void raillens_own_tokens_still_work() throws Exception {
                String name = "o" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                var reg = call(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"passw0rd-long\"}".formatted(name, name)));
                assertThat(me(body(reg).get("token").asText()).getStatus()).isEqualTo(200);
        }

        @Test
        void only_a_nationwide_railops_admin_with_a_one_time_code_is_a_raillens_admin() throws Exception {
                UUID sub = UUID.randomUUID();
                String email = email();
                String withOtp = token(new Claims(sub, email, ISSUER, "railops-api", Instant.now().plusSeconds(600), List.of("ADMIN"),
                                List.of("ADMIN:GLOBAL:*"), List.of("pwd", "otp"), KEYS));
                assertThat(call(get("/api/v1/admin/stats").header("Authorization", "Bearer " + withOtp)).getStatus()).isEqualTo(200);

                String noOtp = token(new Claims(sub, email, ISSUER, "railops-api", Instant.now().plusSeconds(600), List.of("ADMIN"),
                                List.of("ADMIN:GLOBAL:*"), List.of("pwd"), KEYS));
                assertThat(call(get("/api/v1/admin/stats").header("Authorization", "Bearer " + noOtp)).getStatus()).isEqualTo(401);
                String zonal = token(new Claims(sub, email, ISSUER, "railops-api", Instant.now().plusSeconds(600), List.of("ADMIN"),
                                List.of("ADMIN:ZONE:WR"), List.of("pwd", "otp"), KEYS));
                assertThat(call(get("/api/v1/admin/stats").header("Authorization", "Bearer " + zonal)).getStatus()).isEqualTo(401);
        }
}
