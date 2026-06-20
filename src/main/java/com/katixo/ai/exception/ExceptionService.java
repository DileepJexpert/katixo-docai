package com.katixo.ai.exception;

import com.katixo.ai.config.AiProperties;
import com.katixo.ai.extraction.ExtractionStepResult;
import com.katixo.ai.model.ExtractionException;
import com.katixo.ai.validation.ValidationOutcome;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the field-level exception list, computes overall confidence, and decides whether the
 * document must go to the human-review queue (spec 5.6). A document is flagged when confidence is
 * below the configured threshold OR any exception exists.
 */
@Service
public class ExceptionService {

    private final ConfidenceCalculator confidenceCalculator;
    private final AiProperties props;

    public ExceptionService(ConfidenceCalculator confidenceCalculator, AiProperties props) {
        this.confidenceCalculator = confidenceCalculator;
        this.props = props;
    }

    public ReviewAssessment assess(double ocrConfidence, ExtractionStepResult step, ValidationOutcome validation) {
        List<ExtractionException> all = new ArrayList<>();
        all.addAll(step.structuralExceptions());     // JSON_INVALID / SCHEMA_INVALID first
        all.addAll(validation.exceptions());         // then field-level arithmetic/GSTIN/date

        double confidence = confidenceCalculator.compute(
                ocrConfidence, step.jsonValid(), step.repaired(), validation.passRatio(), step.modelPath());

        boolean needsReview = confidence < props.getReview().getThreshold() || !all.isEmpty();
        return new ReviewAssessment(confidence, all, needsReview);
    }
}
