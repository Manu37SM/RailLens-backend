package com.labs.train.train_db.security;

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

import org.springframework.test.context.DynamicPropertyRegistry;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/** A stand-in for RailOps' identity service in tests: a signing key, its JWKS address, and a token builder. */
final class RailOpsTestIssuer {

        static final String ISSUER = "https://railops-identity.example";
        static final KeyPair KEYS = rsa();
        static final KeyPair OTHER_KEYS = rsa();
        private static final HttpServer JWKS = jwksServer();

        private RailOpsTestIssuer() {
        }

        static void register(DynamicPropertyRegistry r) {
                r.add("raillens.railops.jwks-url", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/.well-known/jwks.json");
                r.add("raillens.railops.issuer", () -> ISSUER);
                r.add("raillens.railops.api-url", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort());
        }

        /** Phase 4D fake account endpoints: this password is "right"; a new password "weak-1" is refused with 422. */
        static final String RIGHT_PASSWORD = "right-password-1";
        /** Calls received, as "path|Authorization header|body". */
        static final java.util.Queue<String> ACCOUNT_CALLS = new java.util.concurrent.ConcurrentLinkedQueue<>();

        record Claims(UUID sub, String email, String issuer, String audience, Instant expires, List<String> roles,
                        List<String> grants, List<String> amr, KeyPair keys) {
                static Claims passenger(UUID sub, String email) {
                        return new Claims(sub, email, ISSUER, "railops-api", Instant.now().plusSeconds(600), List.of("PASSENGER"),
                                        List.of(), List.of("pwd"), KEYS);
                }

                static Claims globalAdmin(UUID sub, String email) {
                        return new Claims(sub, email, ISSUER, "railops-api", Instant.now().plusSeconds(600), List.of("ADMIN"),
                                        List.of("ADMIN:GLOBAL:*"), List.of("pwd", "otp"), KEYS);
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

        private static KeyPair rsa() {
                try {
                        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
                        g.initialize(2048);
                        return g.generateKeyPair();
                } catch (Exception e) {
                        throw new IllegalStateException(e);
                }
        }

        private static HttpServer jwksServer() {
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
                        for (String path : List.of("/api/v1/auth/password", "/api/v1/auth/erase")) {
                                s.createContext(path, ex -> {
                                        String sent = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                                        ACCOUNT_CALLS.add(path + "|" + ex.getRequestHeaders().getFirst("Authorization") + "|" + sent);
                                        int status = !sent.contains("\"" + RIGHT_PASSWORD + "\"") ? 401
                                                        : sent.contains("\"weak-1\"") ? 422 : 204;
                                        ex.sendResponseHeaders(status, -1);
                                        ex.close();
                                });
                        }
                        s.setExecutor(null);
                        s.start();
                        return s;   // lives for the whole test JVM, shared by every test class
                } catch (Exception e) {
                        throw new IllegalStateException(e);
                }
        }
}
