package com.bookingsquadra.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    private static final String DEFAULT_CODE = "not_found";

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, DEFAULT_CODE, message);
    }

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
