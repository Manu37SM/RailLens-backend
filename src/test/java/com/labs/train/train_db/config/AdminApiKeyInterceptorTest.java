package com.labs.train.train_db.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Plain unit tests for the fail-closed admin API key gate added while
 * locking down POST /api/admin/import (see the class-level javadoc on
 * AdminApiKeyInterceptor for why this exists). Configures the @Value-backed
 * key field via reflection rather than a Spring context, since the
 * interceptor has no other dependencies worth spinning one up for.
 */
@ExtendWith(MockitoExtension.class)
class AdminApiKeyInterceptorTest {

        private static final String CONFIGURED_KEY = "test-secret-key";

        @Mock
        private HttpServletRequest request;

        @Mock
        private HttpServletResponse response;

        private AdminApiKeyInterceptor interceptor;
        private StringWriter responseBody;

        @BeforeEach
        void setUp() {
                interceptor = new AdminApiKeyInterceptor();
                responseBody = new StringWriter();
        }

        private void setConfiguredKey(String key) throws Exception {
                Field field = AdminApiKeyInterceptor.class.getDeclaredField("configuredKey");
                field.setAccessible(true);
                field.set(interceptor, key);
        }

        // Only stubbed in the rejection tests below - preHandle only calls
        // response.getWriter() on the path that writes an error body, so
        // stubbing it unconditionally in @BeforeEach would leave it unused
        // (and fail) on the "request is allowed through" test.
        private void expectResponseBodyWrite() throws Exception {
                when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        }

        @Test
        void rejectsEverythingWhenKeyNotConfigured() throws Exception {
                setConfiguredKey("");
                expectResponseBodyWrite();

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isFalse();
                verifyRejection(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        @Test
        void rejectsRequestMissingHeader() throws Exception {
                setConfiguredKey(CONFIGURED_KEY);
                expectResponseBodyWrite();
                when(request.getHeader("X-Admin-Key")).thenReturn(null);

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isFalse();
                verifyRejection(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        void rejectsRequestWithWrongKey() throws Exception {
                setConfiguredKey(CONFIGURED_KEY);
                expectResponseBodyWrite();
                when(request.getHeader("X-Admin-Key")).thenReturn("wrong-key");

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isFalse();
                verifyRejection(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        void allowsRequestWithCorrectKey() throws Exception {
                setConfiguredKey(CONFIGURED_KEY);
                when(request.getHeader("X-Admin-Key")).thenReturn(CONFIGURED_KEY);

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isTrue();
        }

        private void verifyRejection(int expectedStatus) {
                verify(response).setStatus(expectedStatus);
                assertThat(responseBody.toString())
                                .contains("\"status\":" + expectedStatus)
                                .contains("\"timestamp\"")
                                .contains("\"error\"");
        }
}
