package com.labs.train.train_db.exception;

/**
 * Thrown by {@code AuthService#login} when an account has too many recent
 * failed login attempts and is temporarily locked (see
 * {@code raillens.auth.max-failed-login-attempts}/
 * {@code raillens.auth.lockout-duration-minutes}). Deliberately distinct
 * from {@link InvalidCredentialsException} - a locked account is a
 * different situation for the client to show ("try again in a few
 * minutes" vs "check your password") and doesn't carry the same
 * enumeration risk, since it only ever fires for an account that already
 * received several failed attempts in a row.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}
