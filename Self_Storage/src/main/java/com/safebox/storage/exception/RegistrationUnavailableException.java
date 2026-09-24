package com.safebox.storage.exception;

public class RegistrationUnavailableException extends RuntimeException {

    public RegistrationUnavailableException() {
        super("Registration is temporarily unavailable. Please try again later.");
    }
}
