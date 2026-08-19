package com.labs.train.train_db.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AdminApiKeyInterceptor adminApiKeyInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final AuthRateLimitInterceptor authRateLimitInterceptor;
    private final JwtAuthInterceptor jwtAuthInterceptor;

    /**
     * Comma-separated list of allowed origins, configurable via
     * {@code raillens.cors.allowed-origins}. Was previously hardcoded to
     * "http://localhost:3000" - harmless for local dev, but it would
     * silently reject every browser request once the frontend is deployed
     * anywhere else (the project's stated goal is public deployment).
     * Defaults to the same local dev value so nothing changes until this
     * is explicitly configured for a real environment.
     */
    @Value("${raillens.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // "/api/**" is a superset of every versioned path (e.g.
        // "/api/v1/trains") so this doesn't need to change when a new API
        // version is introduced - only the interceptor patterns below that
        // reference a specific sub-path (auth, admin) do.
        //
        // Must cover every HTTP method actually exposed - TrainController,
        // StationController and ScheduleController all accept POST in
        // addition to GET. PUT/DELETE added alongside
        // /api/v1/auth/password and /api/v1/auth/me (delete) - without them
        // the browser's CORS preflight rejects those calls before the
        // request body is ever sent.
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Order matters here: rate limiting runs first and applies to all of
        // /api/** (every version, present and future - see the CORS comment
        // above), including /api/v1/admin/**, so a client can't bypass it by
        // hammering the admin route specifically. The admin key check then
        // narrows further to just that one path.
        //
        // Deliberately still just "is there a valid X-Admin-Key /
        // Authorization: Bearer <jwt>" today, not "is there a valid
        // developer API key" - the user has explicitly deferred public API
        // keys until RailLens actually has third-party developers (see
        // project memory). Nothing here needs to change to add that later:
        // a future DeveloperApiKeyInterceptor would just be one more
        // registry.addInterceptor(...).addPathPatterns(...) call, and
        // RateLimitInterceptor already has a single place (the request) to
        // read an optional API key from and grant a higher limit tier.
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");

        registry.addInterceptor(adminApiKeyInterceptor)
                .addPathPatterns("/api/v1/admin/**");

        // Stricter limiter layered on top of the general one, specifically
        // for register/login/me - see AuthRateLimitInterceptor's javadoc.
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/**");

        // Everything under /api/v1/auth/** requires a valid token except
        // register, login, refresh and logout, which either issue a token
        // or work by design without one already being valid (see
        // AuthController's javadoc).
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/v1/auth/**")
                .excludePathPatterns(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout");
    }
}