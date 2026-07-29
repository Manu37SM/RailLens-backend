package com.labs.train.train_db.exception;

/**
 * Thrown on any login failure - unknown username/email, wrong password, or
 * (in principle) a disabled account. Deliberately used for all of those
 * cases rather than distinct exceptions per cause: returning a different
 * message for "no such user" vs "wrong password" lets an attacker enumerate
 * which usernames/emails are registered, one request at a time.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
