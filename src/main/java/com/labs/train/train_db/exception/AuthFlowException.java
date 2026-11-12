package com.labs.train.train_db.exception;

import org.springframework.http.HttpStatus;

/**
 * An authentication step the client must react to, carrying a stable {@code code} (see ApiErrorResponse):
 * MFA_REQUIRED, INVALID_OTP, MFA_ALREADY_ENABLED, MFA_NOT_CONFIGURED, INVALID_ENROLMENT_TOKEN.
 */
public class AuthFlowException extends RuntimeException {

        private final HttpStatus status;
        private final String code;

        public AuthFlowException(HttpStatus status, String code, String message) {
                super(message);
                this.status = status;
                this.code = code;
        }

        public HttpStatus getStatus() {
                return status;
        }

        public String getCode() {
                return code;
        }

        public static AuthFlowException mfaRequired() {
                return new AuthFlowException(HttpStatus.UNAUTHORIZED, "MFA_REQUIRED", "One-time code required");
        }

        public static AuthFlowException invalidOtp() {
                return new AuthFlowException(HttpStatus.UNAUTHORIZED, "INVALID_OTP", "Invalid or already used one-time code");
        }
}
