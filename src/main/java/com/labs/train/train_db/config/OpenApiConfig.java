package com.labs.train.train_db.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

@Configuration
@SecurityScheme(
                name = "bearerAuth",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT",
                in = SecuritySchemeIn.HEADER)
public class OpenApiConfig {

        private static final String DESCRIPTION = """
                        Public REST API for RailLens - Indian Railway train, station and journey \
                        information. Designed for the RailLens web frontend and future Android/iOS \
                        clients; see PROMPT.md for the project's full goals.

                        ## Versioning

                        All routes are prefixed `/api/v1/**`. The version prefix exists so a future \
                        breaking change can ship as `/api/v2/**` alongside `/api/v1/**` instead of \
                        breaking every existing client at once - there is currently only one version \
                        and no breaking change planned. There are no API keys yet either: every read \
                        endpoint below is open, rate-limited by IP. When RailLens has third-party \
                        developers, a key-based higher-usage tier can be layered on top of the \
                        existing rate limiter without changing any of the URLs or request/response \
                        shapes documented here.

                        ## Quick start

                        Every read endpoint under `/api/v1/**` (trains, stations, schedules, stats) \
                        is open - no API key or login required. Rate limiting applies to all of them \
                        (see the `X-RateLimit-*` response headers); back off on HTTP 429.

                        **Look up a train (curl):**
                        ```
                        curl https://api.raillens.example/api/v1/trains/12951
                        ```

                        **Look up a train (JavaScript fetch):**
                        ```js
                        const res = await fetch('https://api.raillens.example/api/v1/trains/12951');
                        const train = await res.json();
                        ```

                        **Search trains between two stations, paginated:**
                        ```
                        curl "https://api.raillens.example/api/v1/journeys?from=NDLS&to=BCT&page=0&size=20"
                        ```

                        ## Authenticated endpoints (`/api/v1/auth/**`)

                        Register or log in to get a short-lived access token plus a long-lived \
                        refresh token:
                        ```
                        curl -X POST https://api.raillens.example/api/v1/auth/login \\
                          -H "Content-Type: application/json" \\
                          -d '{"usernameOrEmail":"you","password":"yourPassword1"}'
                        ```
                        Use `token` as a bearer credential on protected routes \
                        (`GET /api/v1/auth/me`, `PUT /api/v1/auth/password`, \
                        `DELETE /api/v1/auth/me`):
                        ```
                        curl https://api.raillens.example/api/v1/auth/me \\
                          -H "Authorization: Bearer <token>"
                        ```
                        When the access token expires (see `expiresInSeconds`), exchange the \
                        `refreshToken` for a new pair rather than logging in again - it rotates on \
                        every use, so store the new one and discard the old:
                        ```
                        curl -X POST https://api.raillens.example/api/v1/auth/refresh \\
                          -H "Content-Type: application/json" \\
                          -d '{"refreshToken":"<refreshToken>"}'
                        ```

                        No official SDK yet - every endpoint here is plain JSON over HTTPS, so any \
                        standard HTTP client works. If you build one for a language RailLens doesn't \
                        already have client code for, opening a PR is welcome.
                        """;

        @Bean
        public OpenAPI railLensOpenApi() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("RailLens API")
                                                .description(DESCRIPTION)
                                                .version("v1")
                                                .contact(new Contact().name("RailLens")));
        }
}
