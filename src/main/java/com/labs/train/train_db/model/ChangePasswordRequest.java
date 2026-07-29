package com.labs.train.train_db.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

                @NotBlank(message = "currentPassword is required")
                String currentPassword,

                @NotBlank(message = "newPassword is required")
                @Size(min = 8, max = 100, message = "newPassword must be at least 8 characters")
                @Pattern(
                                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                                message = "newPassword must contain at least one letter and one number")
                String newPassword) {
}
