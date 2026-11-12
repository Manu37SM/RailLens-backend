package com.labs.train.train_db.model;

import jakarta.validation.constraints.NotBlank;

public record MfaEnrolRequest(

                @NotBlank(message = "enrolmentToken is required")
                String enrolmentToken) {
}
