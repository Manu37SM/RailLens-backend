package com.labs.train.train_db.exception;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.labs.train.train_db.model.ApiErrorResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Central exception handling for the REST API. Every handler returns the
 * same {@link ApiErrorResponse} shape so clients (web, and eventually
 * mobile) can rely on a single, predictable error contract regardless of
 * which failure occurred.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
                        ResourceNotFoundException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.NOT_FOUND.value(),
                                ex.getMessage());

                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(response);
        }

        /**
         * Thrown by repository lookups such as {@code Optional#orElseThrow()}
         * when a referenced record (e.g. a train or station number supplied in
         * a request body) does not exist. Mapped to 404 for the same reason as
         * {@link ResourceNotFoundException}, just without a caller-supplied
         * message.
         */
        @ExceptionHandler(NoSuchElementException.class)
        public ResponseEntity<ApiErrorResponse> handleNoSuchElement(
                        NoSuchElementException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.NOT_FOUND.value(),
                                "The requested resource could not be found");

                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(response);
        }

        /**
         * Thrown by AuthService on login when the username/email is unknown or
         * the password does not match. Intentionally uses the same generic
         * message for both cases (see the exception's own javadoc) - mapped to
         * 401 Unauthorized.
         */
        @ExceptionHandler(InvalidCredentialsException.class)
        public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
                        InvalidCredentialsException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.UNAUTHORIZED.value(),
                                ex.getMessage());

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(response);
        }

        /**
         * Thrown by AuthService on register when the username or email is
         * already taken. Checked explicitly before attempting the insert (see
         * AuthService#register) so the caller gets a friendly, field-specific
         * message instead of a generic {@link DataIntegrityViolationException}
         * from the unique-constraint violation.
         */
        @ExceptionHandler(DuplicateUserException.class)
        public ResponseEntity<ApiErrorResponse> handleDuplicateUser(
                        DuplicateUserException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.CONFLICT.value(),
                                ex.getMessage());

                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(response);
        }

        /**
         * Thrown when a write violates a database constraint (e.g. a duplicate
         * train number or station code). Mapped to 409 Conflict rather than a
         * raw 500 so clients can distinguish "this already exists" from an
         * unexpected server error.
         */
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
                        DataIntegrityViolationException ex) {

                log.warn("Data integrity violation", ex);

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.CONFLICT.value(),
                                "The request could not be completed because it conflicts with existing data");

                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(response);
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
                        ConstraintViolationException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                ex.getMessage());

                return ResponseEntity.badRequest().body(response);
        }

        /**
         * Thrown when a {@code @Valid}-annotated {@code @RequestBody} fails
         * Bean Validation (e.g. a blank trainNumber on POST /api/trains).
         * Field-level messages are joined into one readable string so the
         * response stays consistent with every other handler here.
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
                        MethodArgumentNotValidException ex) {

                String message = ex.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .collect(Collectors.joining("; "));

                if (message.isBlank()) {
                        message = "Validation failed";
                }

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                message);

                return ResponseEntity.badRequest().body(response);
        }

        /**
         * Catch-all for anything not handled above. Without this, an unexpected
         * failure falls through to Spring Boot's default error page, which is
         * not guaranteed to be JSON and breaks clients that parse the response
         * body. The real exception is logged server-side; only a generic
         * message is returned to the caller.
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {

                log.error("Unhandled exception", ex);

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "An unexpected error occurred. Please try again.");

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(response);
        }
}