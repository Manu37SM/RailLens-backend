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

    @Value("${raillens.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");

        registry.addInterceptor(adminApiKeyInterceptor)
                .addPathPatterns("/api/v1/admin/**");

        // Catalogue writes. These lived outside /api/v1/admin/** and were reachable WITHOUT any credential:
        // anyone could POST trains, stations and schedules into the production database. Reads stay public.
        // EndpointAuthorizationTest fails if any future write endpoint is left unprotected.
        registry.addInterceptor(new WriteMethodsOnly(adminApiKeyInterceptor))
                .addPathPatterns(
                        "/api/v1/trains", "/api/v1/trains/**",
                        "/api/v1/stations", "/api/v1/stations/**",
                        "/api/v1/schedules", "/api/v1/schedules/**");

        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/**");

        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/v1/auth/**")
                .excludePathPatterns(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout");
    }
}