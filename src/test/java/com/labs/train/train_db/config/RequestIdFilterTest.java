package com.labs.train.train_db.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RequestIdFilterTest {

        @Mock
        private HttpServletRequest request;

        @Mock
        private HttpServletResponse response;

        @Mock
        private FilterChain filterChain;

        private final RequestIdFilter filter = new RequestIdFilter();

        @Test
        void generatesAndEchoesARequestIdWhenNoneIsSupplied() throws Exception {
                when(request.getHeader("X-Request-Id")).thenReturn(null);

                filter.doFilter(request, response, filterChain);

                verify(response).setHeader(
                                org.mockito.ArgumentMatchers.eq("X-Request-Id"),
                                org.mockito.ArgumentMatchers.anyString());
                verify(filterChain).doFilter(request, response);
        }

        @Test
        void reusesAnInboundRequestIdInsteadOfGeneratingANewOne() throws Exception {
                when(request.getHeader("X-Request-Id")).thenReturn("upstream-id-123");

                filter.doFilter(request, response, filterChain);

                verify(response).setHeader("X-Request-Id", "upstream-id-123");
        }

        @Test
        void clearsMdcAfterTheRequestCompletesSoThreadReuseCantLeakIt() throws Exception {
                when(request.getHeader("X-Request-Id")).thenReturn("some-id");

                filter.doFilter(request, response, filterChain);

                assertThat(MDC.get("requestId")).isNull();
        }

        @Test
        void generatesAFreshIdInsteadOfTrustingAnInboundValueContainingControlCharacters() throws Exception {
                // A newline here would let a malicious caller inject a forged
                // extra log line into every subsequent log statement for this
                // request - the filter must not pass this through verbatim.
                when(request.getHeader("X-Request-Id")).thenReturn("legit-looking\nERROR fake log line");

                filter.doFilter(request, response, filterChain);

                verify(response).setHeader(
                                org.mockito.ArgumentMatchers.eq("X-Request-Id"),
                                org.mockito.ArgumentMatchers.argThat(id -> !id.contains("\n")));
        }

        @Test
        void generatesAFreshIdInsteadOfTrustingAnExcessivelyLongInboundValue() throws Exception {
                when(request.getHeader("X-Request-Id")).thenReturn("a".repeat(500));

                filter.doFilter(request, response, filterChain);

                verify(response).setHeader(
                                org.mockito.ArgumentMatchers.eq("X-Request-Id"),
                                org.mockito.ArgumentMatchers.argThat(id -> id.length() < 500));
        }
}
