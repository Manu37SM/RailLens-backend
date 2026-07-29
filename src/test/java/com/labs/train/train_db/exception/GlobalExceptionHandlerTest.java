package com.labs.train.train_db.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import com.labs.train.train_db.model.ApiErrorResponse;

class GlobalExceptionHandlerTest {

        private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

        @Test
        void mapsResourceNotFoundTo404WithTheExceptionsOwnMessage() {

                ResponseEntity<ApiErrorResponse> response =
                                handler.handleResourceNotFound(new ResourceNotFoundException("no such train"));

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(response.getBody().error()).isEqualTo("no such train");
        }

        @Test
        void mapsInvalidCredentialsTo401() {

                ResponseEntity<ApiErrorResponse> response =
                                handler.handleInvalidCredentials(
                                                new InvalidCredentialsException("Invalid username/email or password"));

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void mapsDuplicateUserTo409() {

                ResponseEntity<ApiErrorResponse> response =
                                handler.handleDuplicateUser(new DuplicateUserException("Username is already taken"));

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void mapsMissingRequestParameterTo400WithTheParameterNameInTheMessage() {

                MissingServletRequestParameterException ex =
                                new MissingServletRequestParameterException("from", "String");

                ResponseEntity<ApiErrorResponse> response = handler.handleMissingRequestParameter(ex);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody().error()).contains("from");
        }

        @Test
        void mapsUnreadableRequestBodyTo400InsteadOfTheGenericServerErrorHandler() {

                HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                                "JSON parse error", mock(HttpInputMessage.class));

                ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableBody(ex);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void unhandledExceptionsFallBackTo500WithAGenericMessageThatDoesNotLeakInternals() {

                ResponseEntity<ApiErrorResponse> response =
                                handler.handleUnexpected(new RuntimeException("some internal detail"));

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                assertThat(response.getBody().error()).doesNotContain("some internal detail");
        }
}
