package com.labs.train.train_db.config;

import org.springframework.stereotype.Component;

@Component
public class AuthRateLimitInterceptor extends AbstractRateLimitInterceptor {

        private static final int MAX_REQUESTS_PER_WINDOW = 10;
        private static final long WINDOW_MILLIS = 60_000L;

        @Override
        protected int getMaxRequestsPerWindow() {
                return MAX_REQUESTS_PER_WINDOW;
        }

        @Override
        protected long getWindowMillis() {
                return WINDOW_MILLIS;
        }
}
