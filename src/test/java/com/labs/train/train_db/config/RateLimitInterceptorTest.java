package com.labs.train.train_db.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

        @Mock
        private HttpServletRequest request;

        @Mock
        private HttpServletResponse response;

        private RateLimitInterceptor interceptor;

        @BeforeEach
        void setUp() throws Exception {
                interceptor = new RateLimitInterceptor(
                                new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED_PROXIES, ""));
                lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
                lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);
                lenient().when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        }

        @Test
        void allowsRequestsUnderTheLimit() throws Exception {
                for (int i = 0; i < 120; i++) {
                        boolean allowed = interceptor.preHandle(request, response, new Object());
                        assertThat(allowed).as("request #" + (i + 1)).isTrue();
                }
        }

        @Test
        void rejectsOnceLimitIsExceeded() throws Exception {
                for (int i = 0; i < 120; i++) {
                        interceptor.preHandle(request, response, new Object());
                }

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isFalse();
        }

        @Test
        void tracksDifferentClientsIndependently() throws Exception {
                for (int i = 0; i < 120; i++) {
                        interceptor.preHandle(request, response, new Object());
                }

                when(request.getRemoteAddr()).thenReturn("10.0.0.2");

                boolean allowedForDifferentClient = interceptor.preHandle(request, response, new Object());

                assertThat(allowedForDifferentClient).isTrue();
        }

        @Test
        void aRotatingSpoofedForwardedForCannotBypassTheLimit() throws Exception {
                // Before the fix the LEFTMOST X-Forwarded-For entry was the bucket key: a fresh fake IP per request
                // meant a fresh bucket, i.e. no limit at all. Behind Render the real client is the last entry.
                for (int i = 0; i < 120; i++) {
                        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100." + (i % 250) + ", 203.0.113.5");
                        interceptor.preHandle(request, response, new Object());
                }
                when(request.getHeader("X-Forwarded-For")).thenReturn("192.0.2.77, 203.0.113.5");

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isFalse();
        }
}
