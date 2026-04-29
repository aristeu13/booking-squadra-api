package com.bookingsquadra.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

final class ProblemDetails {

    static final String TRACE_ID_KEY = "traceId";
    static final String CODE_KEY = "code";
    static final String TIMESTAMP_KEY = "timestamp";
    static final String ERRORS_KEY = "errors";

    private ProblemDetails() {
    }

    static ProblemDetail of(HttpStatusCode status, String code, String detail, HttpServletRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(reason);
        problem.setType(URI.create("about:blank"));
        problem.setProperty(CODE_KEY, code);
        problem.setProperty(TIMESTAMP_KEY, Instant.now().toString());

        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            problem.setProperty(TRACE_ID_KEY, traceId);
        }

        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }

        return problem;
    }
}
