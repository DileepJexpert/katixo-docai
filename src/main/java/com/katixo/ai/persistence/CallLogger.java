package com.katixo.ai.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.katixo.ai.extraction.ExtractionStepResult;
import com.katixo.ai.model.ExtractionResult;
import com.katixo.ai.validation.ValidationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes the mandatory per-call audit row (spec 5.2.7). All data stays local; this is also the
 * training/eval dataset for the future.
 */
@Service
public class CallLogger {

    private static final Logger log = LoggerFactory.getLogger(CallLogger.class);

    private final CallLogRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CallLogger(CallLogRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void log(ExtractionResult result, String fileHash, String fileName, String ocrText,
                    double ocrConfidence, ExtractionStepResult step, ValidationOutcome validation,
                    long totalLatencyMs) {
        try {
            CallLog entry = new CallLog(
                    UUID.randomUUID().toString(),
                    result.id(),
                    fileHash,
                    fileName,
                    step.modelPath() == null ? null : step.modelPath().wire(),
                    step.modelName(),
                    step.promptVersion(),
                    ocrConfidence,
                    step.llmLatencyMs(),
                    totalLatencyMs,
                    ocrText,
                    step.rawOutput(),
                    writeJson(result),
                    writeJson(validation),
                    Instant.now(clock));
            repository.save(entry);
        } catch (RuntimeException e) {
            // Logging must never break extraction; record the failure and carry on.
            log.warn("Failed to persist call log for record {}: {}", result.id(), e.getMessage());
        }
    }

    private String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
