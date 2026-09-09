package edu.wgu.pantryplan.service;

/**
 * Raised when a registration attempt reuses an existing account email.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("An account already exists for " + email);
    }
}
