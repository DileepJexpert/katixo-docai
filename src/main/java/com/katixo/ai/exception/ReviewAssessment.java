package com.katixo.ai.exception;

import com.katixo.ai.model.ExtractionException;

import java.util.List;

/**
 * The confidence + exceptions + review decision for one document.
 */
public record ReviewAssessment(double confidence, List<ExtractionException> exceptions, boolean needsHumanReview) {

    public ReviewAssessment {
        exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
    }
}
