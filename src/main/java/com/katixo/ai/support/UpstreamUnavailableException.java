package com.katixo.ai.support;

import com.katixo.ai.commons.sidecar.SidecarUnavailableException;

/**
 * Thrown when a local dependency (Ollama or the OCR sidecar) is unreachable or errors out.
 * Mapped to HTTP 503 with a clear message - never a 500/stacktrace to the caller (spec section 7).
 *
 * <p>Extends the shared {@link SidecarUnavailableException} so this app keeps its specific type and
 * message (and its {@code @ExceptionHandler}) while sharing the platform base type.
 */
public class UpstreamUnavailableException extends SidecarUnavailableException {

    public UpstreamUnavailableException(String service, String message, Throwable cause) {
        super(service, message, cause);
    }
}
