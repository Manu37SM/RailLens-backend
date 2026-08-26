package com.labs.train.train_db.model;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(

                @NotBlank(message = "password is required")
                String password) {
}
