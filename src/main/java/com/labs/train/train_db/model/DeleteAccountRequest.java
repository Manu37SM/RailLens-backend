package com.labs.train.train_db.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Requires the current password so account deletion can't be triggered by
 * just a stolen/leaked bearer token alone (e.g. a token copied from a
 * shared computer) - same reasoning as requiring it for
 * ChangePasswordRequest, just for an even less reversible action.
 */
public record DeleteAccountRequest(

                @NotBlank(message = "password is required")
                String password) {
}
