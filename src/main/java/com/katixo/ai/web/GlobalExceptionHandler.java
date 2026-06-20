package com.katixo.ai.web;

import com.katixo.ai.commons.gpu.GpuBusyException;
import com.katixo.ai.support.BadInputException;
import com.katixo.ai.support.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Translates exceptions into clean HTTP responses. A downed Ollama/OCR sidecar becomes a 503 with a
 * clear message; bad input becomes a 400; nothing ever leaks a 500/stacktrace to the caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUpstream(UpstreamUnavailableException e) {
        log.warn("Dependency '{}' unavailable: {}", e.getService(), e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(503, "Service Unavailable", e.getMessage(), e.getService()));
    }

    @ExceptionHandler(GpuBusyException.class)
    public ResponseEntity<ErrorResponse> handleGpuBusy(GpuBusyException e) {
        log.warn("GPU busy: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(503, "Service Unavailable",
                        "The local GPU is busy with another job. Please retry shortly.", "gpu"));
    }

    @ExceptionHandler(BadInputException.class)
    public ResponseEntity<ErrorResponse> handleBadInput(BadInputException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", e.getMessage(), null));
    }

    @ExceptionHandler({MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", e.getMessage(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(413, "Payload Too Large",
                        "Uploaded file exceeds the configured size limit.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        // Log the full detail server-side; return a sanitized message to the caller.
        log.error("Unexpected error handling request", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred while processing the document.", null));
    }
}
