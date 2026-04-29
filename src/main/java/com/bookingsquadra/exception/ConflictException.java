package com.bookingsquadra.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    private static final String DEFAULT_CODE = "conflict";

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, DEFAULT_CODE, message);
    }

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public ConflictException(String code, String message, Throwable cause) {
        super(HttpStatus.CONFLICT, code, message, cause);
    }
}
