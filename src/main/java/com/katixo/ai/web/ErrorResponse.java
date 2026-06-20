package com.katixo.ai.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Clean error body - never a stacktrace to the caller (spec section 7). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Instant timestamp, int status, String error, String message, String dependency) {

    public static ErrorResponse of(int status, String error, String message, String dependency) {
        return new ErrorResponse(Instant.now(), status, error, message, dependency);
    }
}
