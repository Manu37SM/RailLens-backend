package com.labs.train.train_db.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class PaginationConfig {

        private static final int MAX_PAGE_SIZE = 100;

        @Bean
        public PageableHandlerMethodArgumentResolverCustomizer paginationCustomizer() {
                return resolver -> resolver.setMaxPageSize(MAX_PAGE_SIZE);
        }
}
