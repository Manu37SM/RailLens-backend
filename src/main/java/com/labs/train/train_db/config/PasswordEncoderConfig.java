package com.labs.train.train_db.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Single shared {@link PasswordEncoder} bean, injected into {@code
 * AuthService}. Kept in its own tiny config class (rather than instantiated
 * inline) so it's easy to mock in tests and so there's exactly one BCrypt
 * strength setting for the whole app.
 */
@Configuration
public class PasswordEncoderConfig {

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
