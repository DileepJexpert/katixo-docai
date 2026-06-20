package com.katixo.ai.extraction;

import com.katixo.ai.model.ExtractedDocument;
import com.katixo.ai.model.ExtractionException;
import com.katixo.ai.model.ModelPath;

import java.util.List;

/**
 * Output of the LLM extract+parse step, with everything the rest of the pipeline (confidence,
 * logging) needs.
 *
 * @param document             parsed document; may be null only if output was unrecoverable JSON
 * @param modelPath            which path ran (recorded always)
 * @param rawOutput            the final raw model output (for logging/evals)
 * @param jsonValid            true if output parsed AND matched the schema (after optional repair)
 * @param repaired             true if a repair retry was attempted
 * @param structuralExceptions JSON_INVALID / SCHEMA_INVALID exceptions, if any
 */
public record ExtractionStepResult(
        ExtractedDocument document,
        ModelPath modelPath,
        String rawOutput,
        String promptVersion,
        String modelName,
        long llmLatencyMs,
        boolean jsonValid,
        boolean repaired,
        List<ExtractionException> structuralExceptions
) {
    public ExtractionStepResult {
        structuralExceptions = structuralExceptions == null ? List.of() : List.copyOf(structuralExceptions);
    }
}
