package com.mossyanwar.ordersinvoicing.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

/**
 * Problem responses for idempotency failures. They extend {@link ErrorResponseException}, which
 * Spring MVC already renders as a problem detail, so no extra exception handler is needed.
 */
final class IdempotencyProblems {

    private static final URI TYPE = URI.create("https://orders-invoicing.example/problems/idempotency-key");

    private IdempotencyProblems() {
    }

    static ErrorResponseException invalidKey() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key",
                "Idempotency-Key must be 1 to 100 characters of letters, digits, '-', '_', ':' or '.'");
    }

    static ErrorResponseException reusedWithDifferentRequest() {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency-Key reused",
                "This Idempotency-Key was already used with a different request body.");
    }

    static ErrorResponseException stillInProgress() {
        return problem(HttpStatus.CONFLICT, "Request in progress",
                "A request with this Idempotency-Key is still being processed. Retry shortly.");
    }

    private static ErrorResponseException problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(TYPE);
        problem.setTitle(title);
        return new ErrorResponseException(status, problem, null);
    }
}
