package com.bookingsquadra.exception;

import org.springframework.http.HttpStatus;

public class UnprocessableEntityException extends ApiException {

    private static final String DEFAULT_CODE = "unprocessable_content";

    public UnprocessableEntityException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, DEFAULT_CODE, message);
    }

    public UnprocessableEntityException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
