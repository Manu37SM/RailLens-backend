package com.labs.train.train_db.config;

import java.util.Set;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies an interceptor only to state-changing methods, so a path like {@code /api/v1/trains} can stay publicly
 * readable (GET) while writes to it need the admin key.
 */
final class WriteMethodsOnly implements HandlerInterceptor {

        private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

        private final HandlerInterceptor delegate;

        WriteMethodsOnly(HandlerInterceptor delegate) {
                this.delegate = delegate;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                        throws Exception {
                if (READ_METHODS.contains(request.getMethod().toUpperCase())) {
                        return true;
                }
                return delegate.preHandle(request, response, handler);
        }
}
