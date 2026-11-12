package com.labs.train.train_db.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code code} (V7) is a stable machine-readable reason for errors a client must react to, e.g. MFA_REQUIRED to show
 * the one-time-code field. Omitted when null, so every existing error body is unchanged.
 */
public record ApiErrorResponse(
                LocalDateTime timestamp,
                int status,
                String error,
                @JsonInclude(JsonInclude.Include.NON_NULL)
                String code) {

        public ApiErrorResponse(LocalDateTime timestamp, int status, String error) {
                this(timestamp, status, error, null);
        }
}
