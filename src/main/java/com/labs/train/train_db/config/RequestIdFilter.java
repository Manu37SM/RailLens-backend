package com.labs.train.train_db.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stamps every request with a correlation ID, before anything else runs
 * (see the {@code @Order} value - lower than {@link SecurityHeadersFilter}'s
 * 1, so the ID is available in MDC for every log line any later
 * filter/interceptor/controller emits, including rejections logged by
 * {@link RateLimitInterceptor} and {@link AdminApiKeyInterceptor}).
 *
 * Reuses an inbound {@code X-Request-Id} if one is already present (e.g.
 * set by a reverse proxy or load balancer in front of this app) so a
 * request can be traced end-to-end across hops, rather than generating a
 * new, disconnected ID at every layer. Echoes the ID back in the response
 * header so a client (including the Next.js frontend) can log/report it
 * when asking for help debugging a specific failed request - there's no
 * log aggregation/APM tool wired up yet (see PROMPT.md's "Monitoring
 * readiness" goal), so for now this is what makes grep-ing a single
 * request's log lines out of a plain text log file possible at all.
 */
@Component
@Order(0)
public class RequestIdFilter extends OncePerRequestFilter {

        private static final String HEADER_NAME = "X-Request-Id";
        private static final String MDC_KEY = "requestId";

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {

                String requestId = request.getHeader(HEADER_NAME);

                if (requestId == null || requestId.isBlank()) {
                        requestId = UUID.randomUUID().toString();
                }

                response.setHeader(HEADER_NAME, requestId);
                MDC.put(MDC_KEY, requestId);

                try {
                        filterChain.doFilter(request, response);
                } finally {
                        // MDC is thread-local and Tomcat's worker threads are pooled and
                        // reused across requests - without this, a request that doesn't
                        // set its own ID (there isn't one, since we always do above) could
                        // otherwise inherit a stale value from whatever request last ran
                        // on that thread.
                        MDC.remove(MDC_KEY);
                }
        }
}
