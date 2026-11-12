package com.labs.train.train_db.security;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Checks access tokens issued by RailOps' login (the single login for both apps, Phase 4B): RS256 signature by a
 * key RailOps publishes at its JWKS address, the configured issuer, audience {@code railops-api}, not expired.
 * RailLens' own HS256 secret is never involved or shared.
 *
 * <p>Off unless {@code raillens.railops.jwks-url} is set; then {@code raillens.railops.issuer} is required.
 * Keys are cached by the decoder and refetched when RailOps rotates them.
 */
@Slf4j
@Component
public class RailOpsTokenVerifier {

        static final String AUDIENCE = "railops-api";
        static final String GLOBAL_ADMIN_GRANT = "ADMIN:GLOBAL:*";

        /** Who the token is for. admin = RailOps admin with a nationwide grant who signed in with a one-time code. */
        public record Verified(UUID userId, String email, boolean admin) {
        }

        private final JwtDecoder decoder;

        public RailOpsTokenVerifier(@Value("${raillens.railops.jwks-url:}") String jwksUrl,
                        @Value("${raillens.railops.issuer:}") String issuer) {
                if (jwksUrl == null || jwksUrl.isBlank()) {
                        this.decoder = null;
                        return;
                }
                if (issuer == null || issuer.isBlank()) {
                        throw new IllegalStateException("raillens.railops.jwks-url is set but raillens.railops.issuer is not");
                }
                var http = new SimpleClientHttpRequestFactory();
                http.setConnectTimeout(Duration.ofSeconds(5));
                http.setReadTimeout(Duration.ofSeconds(20));   // a sleeping free RailOps can be slow to answer once
                NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(jwksUrl.trim())
                                .jwsAlgorithm(SignatureAlgorithm.RS256)
                                .restOperations(new RestTemplate(http))
                                .build();
                nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                                JwtValidators.createDefaultWithIssuer(issuer.trim()),
                                jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                                                ? OAuth2TokenValidatorResult.success()
                                                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "wrong audience", null))));
                this.decoder = nimbus;
                log.info("Accepting RailOps access tokens from issuer {}", issuer.trim());
        }

        public boolean isEnabled() {
                return decoder != null;
        }

        public Optional<Verified> verify(String token) {
                if (decoder == null) {
                        return Optional.empty();
                }
                try {
                        Jwt jwt = decoder.decode(token);
                        String email = jwt.getClaimAsString("email");
                        if (email == null || email.isBlank()) {
                                return Optional.empty();   // RailOps tokens carry it since Phase 4A
                        }
                        List<String> roles = list(jwt, "roles");
                        List<String> grants = list(jwt, "grants");
                        List<String> amr = list(jwt, "amr");
                        boolean admin = roles.contains("ADMIN") && grants.contains(GLOBAL_ADMIN_GRANT) && amr.contains("otp");
                        return Optional.of(new Verified(UUID.fromString(jwt.getSubject()), email.trim(), admin));
                } catch (JwtException | IllegalArgumentException e) {
                        log.debug("Rejecting RailOps token: {}", e.getMessage());
                        return Optional.empty();
                }
        }

        private static List<String> list(Jwt jwt, String claim) {
                List<String> values = jwt.getClaimAsStringList(claim);
                return values == null ? List.of() : values;
        }
}
