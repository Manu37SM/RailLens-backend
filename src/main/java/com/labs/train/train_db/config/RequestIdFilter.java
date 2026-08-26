package com.labs.train.train_db.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(0)
public class RequestIdFilter extends OncePerRequestFilter {

        private static final String HEADER_NAME = "X-Request-Id";
        private static final String MDC_KEY = "requestId";

        private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {

                String requestId = request.getHeader(HEADER_NAME);

                if (requestId == null || !VALID_REQUEST_ID.matcher(requestId).matches()) {
                        requestId = UUID.randomUUID().toString();
                }

                response.setHeader(HEADER_NAME, requestId);
                MDC.put(MDC_KEY, requestId);

                try {
                        filterChain.doFilter(request, response);
                } finally {
                        MDC.remove(MDC_KEY);
                }
        }
}
