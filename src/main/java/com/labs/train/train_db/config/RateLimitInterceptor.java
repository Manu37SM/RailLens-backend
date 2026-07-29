package com.labs.train.train_db.config;

import org.springframework.stereotype.Component;

/**
 * General-purpose limiter for all of /api/**. See {@link
 * AbstractRateLimitInterceptor} for the shared mechanics and documented
 * limitations (in-memory/per-instance, X-Forwarded-For spoofability).
 *
 * PROMPT.md calls out rate limiting as a core production-readiness
 * requirement. This threshold (120/min) is generous enough for normal
 * browsing/search-as-you-type traffic; {@code AuthRateLimitInterceptor}
 * applies a much stricter threshold specifically to /api/auth/** to blunt
 * credential-stuffing/brute-force attempts.
 */
@Component
public class RateLimitInterceptor extends AbstractRateLimitInterceptor {

        private static final int MAX_REQUESTS_PER_WINDOW = 120;
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
