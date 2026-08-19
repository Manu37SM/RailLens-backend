package com.labs.train.train_db.exception;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
         * Thrown by AuthService#login when an account has too many recent
         * failed login attempts (see raillens.auth.max-failed-login-attempts/
         * raillens.auth.lockout-duration-minutes). 423 Locked rather than 401
         * so the frontend can show "try again later" instead of "check your
         * password" - the account is genuinely locked here, a correct
         * password would still be rejected.
         */
        @ExceptionHandler(AccountLockedException.class)
        public ResponseEntity<ApiErrorResponse> handleAccountLocked(
                        AccountLockedException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.LOCKED.value(),
                                ex.getMessage());

                return ResponseEntity.status(HttpStatus.LOCKED)
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
         * Bean Validation (e.g. a blank trainNumber on POST /api/v1/trains).
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
         * Thrown when a required {@code @RequestParam} (e.g. {@code from}/
         * {@code to} on GET /api/v1/journeys, {@code q} on the search endpoints)
         * is missing entirely from the request. Without this handler it fell
         * through to the generic 500 handler below - a client-side mistake
         * (forgot a query param) was being reported as a server failure,
         * which is exactly backwards and would be actively misleading for a
         * future mobile client trying to distinguish "my request was bad"
         * from "the server broke."
         */
        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
                        MissingServletRequestParameterException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Required parameter '" + ex.getParameterName() + "' is missing");

                return ResponseEntity.badRequest().body(response);
        }

        /**
         * Thrown when a {@code @RequestBody} can't be parsed as JSON at all
         * (malformed body, wrong content-type, empty body where one was
         * required) - distinct from {@link MethodArgumentNotValidException},
         * which only fires once the body has already been successfully
         * deserialized into a DTO. Without this handler a client sending
         * broken JSON to e.g. POST /api/v1/auth/register got a generic 500
         * instead of a 400 pointing at their own mistake.
         */
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
                        HttpMessageNotReadableException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Request body is missing or not valid JSON");

                return ResponseEntity.badRequest().body(response);
        }

        /**
         * Thrown when a {@code @PathVariable}/{@code @RequestParam} can't be
         * converted to the type the handler method expects (e.g. a
         * non-numeric value where a path builds a {@code Long}). No current
         * endpoint has a numeric path variable, but this is cheap, general
         * hardening against a 500 the moment one is added - the same
         * "client's fault, not the server's" reasoning as the missing-param
         * and unreadable-body handlers above.
         */
        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
                        MethodArgumentTypeMismatchException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Parameter '" + ex.getName() + "' has an invalid value");

                return ResponseEntity.badRequest().body(response);
        }

        /**
         * Thrown by Spring MVC itself when no controller mapping (and no
         * static resource) matches the request path - e.g. a browser or
         * uptime check hitting {@code GET /} on this API-only backend, which
         * has no root page. Without this handler it fell through to the
         * catch-all below: logged as a server ERROR with a full stack trace,
         * and returned to the caller as a 500 - both wrong for what is just
         * "no route here." Deliberately not logged as an error; an
         * unmapped path is routine, not a server failure.
         */
        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
                        NoResourceFoundException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.NOT_FOUND.value(),
                                "The requested endpoint does not exist");

                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(response);
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