package com.bookingsquadra.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    private static final String DEFAULT_CODE = "bad_request";

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, DEFAULT_CODE, message);
    }

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
