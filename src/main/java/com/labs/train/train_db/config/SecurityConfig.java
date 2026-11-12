package com.labs.train.train_db.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.labs.train.train_db.service.JwtService;

/**
 * Deny-by-default authorization (replaces the path-pattern interceptors, which were allow-by-default).
 *
 * <p>Every rule below is an explicit allowance; anything not listed is refused by {@code denyAll()}, so a new
 * endpoint is closed until someone decides who may call it. EndpointAuthorizationTest checks the result end to end.
 *
 * <p>Credentials are unchanged for clients:
 * <ul>
 *   <li>{@code Authorization: Bearer <jwt>} from /api/v1/auth/login|register|refresh grants ROLE_USER.</li>
 *   <li>{@code X-Admin-Key} grants ROLE_ADMIN (until Phase 2 replaces the shared key with admin accounts + MFA).</li>
 * </ul>
 * Error bodies keep the existing {timestamp,status,error} shape the web and mobile clients parse.
 */
@Configuration
public class SecurityConfig {

        /** Read-only, public catalogue: what the web and mobile apps browse without logging in. */
        static final String[] PUBLIC_READS = {
                        "/api/v1/trains/**", "/api/v1/stations/**", "/api/v1/journeys/**",
                        "/api/v1/network/**", "/api/v1/search/**", "/api/v1/stats/**"};

        /** You can't hold a credential before registering or logging in. */
        static final String[] PUBLIC_AUTH = {
                        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"};

        /** Catalogue writes and operations. */
        static final String[] ADMIN_ONLY = {
                        "/api/v1/admin/**", "/api/v1/trains/**", "/api/v1/stations/**", "/api/v1/schedules/**"};

        @Bean
        SecurityFilterChain api(HttpSecurity http, JwtService jwtService, JsonAuthErrorHandler errors,
                        @Value("${raillens.admin.api-key:}") String adminKey) throws Exception {
                var jwt = new JwtAuthenticationFilter(jwtService);
                var admin = new AdminKeyAuthenticationFilter(errors, adminKey);
                http
                                .csrf(AbstractHttpConfigurer::disable)          // no cookies: bearer tokens only (until Phase 2)
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .logout(AbstractHttpConfigurer::disable)
                                .requestCache(AbstractHttpConfigurer::disable)
                                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                // SecurityHeadersFilter is the single source of security headers. Spring's defaults would
                                // also add "Cache-Control: no-cache" to every response, defeating caching of public reads.
                                .headers(AbstractHttpConfigurer::disable)
                                .cors(c -> {
                                })
                                .addFilterBefore(admin, AuthorizationFilter.class)
                                .addFilterBefore(jwt, AdminKeyAuthenticationFilter.class)
                                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                                .authorizeHttpRequests(a -> a
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // CORS preflight
                                                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                                                "/error").permitAll()
                                                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
                                                                "/v3/api-docs/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, PUBLIC_READS).permitAll()
                                                .requestMatchers(HttpMethod.HEAD, PUBLIC_READS).permitAll()
                                                .requestMatchers(HttpMethod.POST, PUBLIC_AUTH).permitAll()
                                                .requestMatchers("/api/v1/auth/**").hasRole("USER")
                                                .requestMatchers(ADMIN_ONLY).hasRole("ADMIN")
                                                .anyRequest().denyAll());
                return http.build();
        }

        /**
         * Spring Security wires CORS automatically only when a UrlBasedCorsConfigurationSource bean exists (moved here
         * from WebConfig.addCorsMappings, which the security filter would have bypassed: browser preflights rejected).
         * Same origins, methods and headers as before.
         */
        @Bean
        UrlBasedCorsConfigurationSource corsConfigurationSource(
                        @Value("${raillens.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins) {
                CorsConfiguration api = new CorsConfiguration();
                api.setAllowedOrigins(Arrays.asList(allowedOrigins));
                api.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
                api.setAllowedHeaders(List.of("*"));
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/api/**", api);
                return source;
        }

        /**
         * No form or basic login exists. Declaring an empty user store stops Spring Boot from generating a default
         * "user" account with a random password printed to the log.
         */
        @Bean
        UserDetailsService noLocalUsers() {
                return new InMemoryUserDetailsManager();
        }
}
