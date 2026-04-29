package com.bookingsquadra.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    private static final String DEFAULT_CODE = "unauthorized";

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, DEFAULT_CODE, message);
    }

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
