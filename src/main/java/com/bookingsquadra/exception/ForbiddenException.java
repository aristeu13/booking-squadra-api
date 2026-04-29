package com.bookingsquadra.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

    private static final String DEFAULT_CODE = "forbidden";

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, DEFAULT_CODE, message);
    }

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
