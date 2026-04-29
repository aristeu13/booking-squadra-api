package com.bookingsquadra.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {

    private static final String DEFAULT_CODE = "rate_limited";

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, DEFAULT_CODE, message);
    }
}
