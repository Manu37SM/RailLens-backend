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
        // Must cover every HTTP method actually exposed under /api/** -
        // TrainController, StationController and ScheduleController all
        // accept POST in addition to GET, so restricting this to GET only
        // silently breaks those endpoints for any browser-based client.
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Order matters here: rate limiting runs first and applies to all of
        // /api/**, including /api/admin/**, so a client can't bypass it by
        // hammering the admin route specifically. The admin key check then
        // narrows further to just that one path.
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");

        registry.addInterceptor(adminApiKeyInterceptor)
                .addPathPatterns("/api/admin/**");

        // Stricter limiter layered on top of the general one, specifically
        // for register/login/me - see AuthRateLimitInterceptor's javadoc.
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns("/api/auth/**");

        // Only /api/auth/me requires a valid token; register and login are
        // the routes that issue one in the first place and must stay open.
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/auth/me");
    }
}