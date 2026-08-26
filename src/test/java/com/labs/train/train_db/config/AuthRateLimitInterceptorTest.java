package com.labs.train.train_db.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

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
class AuthRateLimitInterceptorTest {

        @Mock
        private HttpServletRequest request;

        @Mock
        private HttpServletResponse response;

        private AuthRateLimitInterceptor interceptor;

        @BeforeEach
        void setUp() throws Exception {
                interceptor = new AuthRateLimitInterceptor();
                lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
                lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);
                lenient().when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        }

        @Test
        void allowsUpToTenRequestsPerMinute() throws Exception {
                for (int i = 0; i < 10; i++) {
                        boolean allowed = interceptor.preHandle(request, response, new Object());
                        assertThat(allowed).as("request #" + (i + 1)).isTrue();
                }
        }

        @Test
        void rejectsTheEleventhRequestWithinTheWindow() throws Exception {
                for (int i = 0; i < 10; i++) {
                        interceptor.preHandle(request, response, new Object());
                }

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isFalse();
        }
}
