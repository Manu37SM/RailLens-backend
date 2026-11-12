package com.labs.train.train_db.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaConfirmRequest(

                @NotBlank(message = "enrolmentToken is required")
                String enrolmentToken,

                @NotBlank(message = "otp is required")
                @Pattern(regexp = "\\d{6}", message = "otp must be 6 digits")
                String otp) {
}
