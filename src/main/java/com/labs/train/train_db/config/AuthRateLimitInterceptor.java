package com.labs.train.train_db.config;

import org.springframework.stereotype.Component;

/**
 * Stricter limiter applied only to /api/auth/** (register/login/me), on top
 * of - not instead of - the general {@link RateLimitInterceptor}. Login and
 * register are the two routes where a generous 120/min limit is actually
 * dangerous: it would let an attacker try 120 password guesses a minute
 * against a single account. 10/min is enough for a real user who fat-
 * fingers their password a couple of times, not enough for a meaningful
 * brute-force attempt from one IP.
 */
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
