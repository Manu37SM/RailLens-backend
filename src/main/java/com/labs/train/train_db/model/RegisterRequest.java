package com.labs.train.train_db.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

                @NotBlank(message = "username is required")
                @Size(min = 3, max = 30, message = "username must be between 3 and 30 characters")
                @Pattern(
                                regexp = "^[a-zA-Z0-9_]+$",
                                message = "username may only contain letters, numbers and underscores")
                String username,

                @NotBlank(message = "email is required")
                @Email(message = "email must be a valid email address")
                @Size(max = 255, message = "email must be at most 255 characters")
                String email,

                @NotBlank(message = "password is required")
                @Size(min = 8, max = 100, message = "password must be at least 8 characters")
                @Pattern(
                                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                                message = "password must contain at least one letter and one number")
                String password) {
}
