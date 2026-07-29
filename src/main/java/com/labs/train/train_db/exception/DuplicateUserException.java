package com.labs.train.train_db.exception;

/**
 * Thrown during registration when the requested username or email is
 * already taken. Checked explicitly via UserRepository#existsByUsername /
 * #existsByEmail before the insert so the caller gets a specific,
 * field-level message rather than a generic unique-constraint failure.
 */
public class DuplicateUserException extends RuntimeException {

    public DuplicateUserException(String message) {
        super(message);
    }
}
