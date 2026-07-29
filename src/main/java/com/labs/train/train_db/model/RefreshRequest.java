package com.labs.train.train_db.model;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

                @NotBlank(message = "refreshToken is required")
                String refreshToken) {
}
