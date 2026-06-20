package com.katixo.ai.support;

/** Thrown for bad caller input (empty file, unsupported content type). Mapped to HTTP 400. */
public class BadInputException extends RuntimeException {
    public BadInputException(String message) {
        super(message);
    }
}
