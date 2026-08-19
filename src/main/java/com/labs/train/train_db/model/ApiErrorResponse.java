package com.labs.train.train_db.model;

import java.time.LocalDateTime;

public record ApiErrorResponse(
                LocalDateTime timestamp,
                int status,
                String error) {
}