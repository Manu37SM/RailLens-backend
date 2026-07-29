package com.labs.train.train_db.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.service.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class JwtAuthInterceptorTest {

        @Mock
        private HttpServletRequest request;

        @Mock
        private HttpServletResponse response;

        @Mock
        private JwtService jwtService;

        private JwtAuthInterceptor interceptor;

        private void expectResponseBodyWrite() throws Exception {
                when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        }

        @Test
        void rejectsARequestWithNoAuthorizationHeader() throws Exception {
                interceptor = new JwtAuthInterceptor(jwtService);
                when(request.getHeader("Authorization")).thenReturn(null);
                expectResponseBodyWrite();

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isFalse();
                verify(response).setStatus(401);
        }

        @Test
        void rejectsARequestWhoseAuthorizationHeaderIsNotBearer() throws Exception {
                interceptor = new JwtAuthInterceptor(jwtService);
                when(request.getHeader("Authorization")).thenReturn("Basic abc123");
                expectResponseBodyWrite();

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isFalse();
        }

        @Test
        void rejectsAnInvalidToken() throws Exception {
                interceptor = new JwtAuthInterceptor(jwtService);
                when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
                when(jwtService.validateAndGetUsername("bad-token")).thenReturn(null);
                expectResponseBodyWrite();

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isFalse();
        }

        @Test
        void allowsAValidTokenAndStashesTheUsernameAsARequestAttribute() throws Exception {
                interceptor = new JwtAuthInterceptor(jwtService);
                when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
                when(jwtService.validateAndGetUsername("good-token")).thenReturn("manish");

                boolean allowed = interceptor.preHandle(request, response, new Object());

                assertThat(allowed).isTrue();
                verify(request).setAttribute("authenticatedUsername", "manish");
        }
}
