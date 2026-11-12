package com.labs.train.train_db.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

        @Test
        void hstsIsSentEvenWhenTheAppSeesPlainHttpBehindATlsProxy() throws Exception {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trains");
                request.setSecure(false);   // what the app sees behind Render's TLS-terminating proxy
                MockHttpServletResponse response = new MockHttpServletResponse();

                new SecurityHeadersFilter().doFilter(request, response, new MockFilterChain());

                assertThat(response.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
                assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
                assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        }
}
