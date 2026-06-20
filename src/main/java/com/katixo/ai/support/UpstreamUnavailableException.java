package com.katixo.ai.support;

/**
 * Thrown when a local dependency (Ollama or the OCR sidecar) is unreachable or errors out.
 * Mapped to HTTP 503 with a clear message - never a 500/stacktrace to the caller (spec section 7).
 */
public class UpstreamUnavailableException extends RuntimeException {

    private final String service;

    public UpstreamUnavailableException(String service, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
