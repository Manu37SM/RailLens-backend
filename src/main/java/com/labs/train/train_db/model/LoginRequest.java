package com.labs.train.train_db.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(

                @NotBlank(message = "usernameOrEmail is required")
                String usernameOrEmail,

                @NotBlank(message = "password is required")
                String password,

                /** 6-digit authenticator code; required for admin accounts with MFA, ignored otherwise. */
                @Pattern(regexp = "\\d{6}", message = "otp must be 6 digits")
                String otp) {

        public LoginRequest(String usernameOrEmail, String password) {
                this(usernameOrEmail, password, null);
        }
}
