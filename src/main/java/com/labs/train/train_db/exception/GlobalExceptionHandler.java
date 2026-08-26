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

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
                        MissingServletRequestParameterException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Required parameter '" + ex.getParameterName() + "' is missing");

                return ResponseEntity.badRequest().body(response);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
                        HttpMessageNotReadableException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Request body is missing or not valid JSON");

                return ResponseEntity.badRequest().body(response);
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
                        MethodArgumentTypeMismatchException ex) {

                ApiErrorResponse response = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Parameter '" + ex.getName() + "' has an invalid value");

                return ResponseEntity.badRequest().body(response);
        }

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