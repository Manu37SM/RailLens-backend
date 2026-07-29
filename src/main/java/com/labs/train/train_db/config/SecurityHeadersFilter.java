package com.labs.train.train_db.config;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds baseline security response headers to every request. There is no
 * Spring Security dependency in this project (see AdminApiKeyInterceptor's
 * javadoc for the broader auth context), so these headers - which Spring
 * Security's {@code headers()} DSL would normally set for free - have to be
 * added by hand here instead.
 *
 * Deliberately conservative: this is a pure API backend with no server-rendered
 * HTML of its own, so most of what's set here is standard baseline hardening
 * rather than a tuned Content-Security-Policy (which belongs on the Next.js
 * frontend that actually renders HTML - see PROMPT.md's security section).
 */
@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {

                // Stops browsers from guessing/"sniffing" a response's content
                // type - relevant here because a maliciously crafted train/station
                // name stored via the write endpoints could otherwise be
                // reinterpreted as HTML/JS by an old browser if ever rendered
                // directly instead of through React's escaping.
                response.setHeader("X-Content-Type-Options", "nosniff");

                // This API is never meant to be embedded in an iframe.
                response.setHeader("X-Frame-Options", "DENY");

                // Don't leak the full referring URL (which may contain query
                // params like search terms) to third parties when a client
                // follows an external link found in API-served data.
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                // This is a JSON API - nothing here should ever run browser
                // features like camera/microphone/geolocation.
                response.setHeader(
                                "Permissions-Policy",
                                "geolocation=(), camera=(), microphone=()");

                // Only meaningful over an actual HTTPS connection - setting it on
                // plain HTTP (e.g. local dev) would be a no-op at best and
                // confusing at worst, so it's gated on request.isSecure(). In
                // most real deployments HTTPS is terminated at a reverse proxy
                // in front of this app; check that the proxy forwards
                // X-Forwarded-Proto (and that Tomcat is configured to trust it)
                // if this header isn't showing up as expected in production.
                if (request.isSecure()) {
                        response.setHeader(
                                        "Strict-Transport-Security",
                                        "max-age=31536000; includeSubDomains");
                }

                filterChain.doFilter(request, response);
        }
}
