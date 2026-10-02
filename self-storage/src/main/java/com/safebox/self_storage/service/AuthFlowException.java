package com.safebox.self_storage.service;

import org.springframework.http.HttpStatus;

public class AuthFlowException extends RuntimeException {
    private final HttpStatus status;
    public AuthFlowException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
