package com.labs.train.train_db.config;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractRateLimitInterceptor implements HandlerInterceptor {

        private static final long SWEEP_EVERY_N_REQUESTS = 1000L;

        private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
        private final AtomicLong requestCounter = new AtomicLong();

        protected abstract int getMaxRequestsPerWindow();

        protected abstract long getWindowMillis();

        @Override
        public boolean preHandle(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Object handler) throws Exception {

                String clientKey = resolveClientKey(request);
                long now = System.currentTimeMillis();
                long windowMillis = getWindowMillis();

                Bucket bucket = buckets.computeIfAbsent(clientKey, key -> new Bucket(now));

                int currentCount;
                synchronized (bucket) {
                        if (now - bucket.windowStart >= windowMillis) {
                                bucket.windowStart = now;
                                bucket.count.set(0);
                        }
                        currentCount = bucket.count.incrementAndGet();
                }

                if (requestCounter.incrementAndGet() % SWEEP_EVERY_N_REQUESTS == 0) {
                        sweepStaleBuckets(now, windowMillis);
                }

                if (currentCount > getMaxRequestsPerWindow()) {
                        log.warn("Rate limit exceeded for {} on {} {}",
                                        clientKey, request.getMethod(), request.getRequestURI());
                        reject(response, windowMillis);
                        return false;
                }

                return true;
        }

        private String resolveClientKey(HttpServletRequest request) {

                String forwardedFor = request.getHeader("X-Forwarded-For");

                if (forwardedFor != null && !forwardedFor.isBlank()) {
                        return forwardedFor.split(",")[0].trim();
                }

                return request.getRemoteAddr();
        }

        private void sweepStaleBuckets(long now, long windowMillis) {
                buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStart > windowMillis * 2);
        }

        private void reject(HttpServletResponse response, long windowMillis) throws IOException {

                String json = "{\"status\":429,\"error\":\"Too many requests - please slow down and try again shortly.\"}";

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", String.valueOf(windowMillis / 1000));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(json);
        }

        private static final class Bucket {
                private volatile long windowStart;
                private final AtomicInteger count = new AtomicInteger(0);

                private Bucket(long windowStart) {
                        this.windowStart = windowStart;
                }
        }
}
