package com.labs.train.train_db.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * Caps the page size any caller (including the paginated GET /api/trains
 * and /api/stations endpoints) can request via ?size=. Spring Data's
 * {@link org.springframework.data.domain.Pageable} has no upper bound by
 * default - without this, a client (malicious or just buggy) could request
 * {@code ?size=1000000} and force the service to load the entire table in
 * one response, defeating the point of pagination existing at all (see
 * Enhancement 7) and putting real memory/latency pressure on the instance.
 * Spring Boot auto-detects this customizer bean and applies it to every
 * {@code Pageable} argument resolved from a request.
 */
@Configuration
public class PaginationConfig {

        private static final int MAX_PAGE_SIZE = 100;

        @Bean
        public PageableHandlerMethodArgumentResolverCustomizer paginationCustomizer() {
                return resolver -> resolver.setMaxPageSize(MAX_PAGE_SIZE);
        }
}
