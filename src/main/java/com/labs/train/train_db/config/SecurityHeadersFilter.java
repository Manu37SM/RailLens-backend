package com.labs.train.train_db.config;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {

                response.setHeader("X-Content-Type-Options", "nosniff");

                response.setHeader("X-Frame-Options", "DENY");

                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                response.setHeader(
                                "Permissions-Policy",
                                "geolocation=(), camera=(), microphone=()");

                if (request.isSecure()) {
                        response.setHeader(
                                        "Strict-Transport-Security",
                                        "max-age=31536000; includeSubDomains");
                }

                filterChain.doFilter(request, response);
        }
}
