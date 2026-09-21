package com.safebox.storage.exception;

public class InactiveUserException extends RuntimeException {

    public InactiveUserException() {
        super("User account is not active");
    }
}
