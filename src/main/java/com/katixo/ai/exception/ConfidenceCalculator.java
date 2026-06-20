package com.katixo.ai.exception;

import com.katixo.ai.model.ModelPath;
import org.springframework.stereotype.Component;

/**
 * Combines the signals from across the pipeline into a single 0..1 confidence (spec 5.6):
 * OCR confidence, JSON validity, and the fraction of deterministic checks that passed.
 */
@Component
public class ConfidenceCalculator {

    private static final double W_SOURCE = 0.40;     // OCR (or VLM prior)
    private static final double W_JSON = 0.20;       // model produced valid, schema-conformant JSON
    private static final double W_VALIDATION = 0.40; // arithmetic/GSTIN/date checks passed

    /** On the VLM path OCR was not trusted, so use a neutral prior for the "source" term. */
    private static final double VLM_SOURCE_PRIOR = 0.70;

    public double compute(double ocrConfidence, boolean jsonValid, boolean repaired,
                          double validationPassRatio, ModelPath path) {
        double source = path == ModelPath.VLM ? VLM_SOURCE_PRIOR : clamp01(ocrConfidence);
        double json = jsonValid ? (repaired ? 0.9 : 1.0) : 0.0;
        double validation = clamp01(validationPassRatio);

        double confidence = W_SOURCE * source + W_JSON * json + W_VALIDATION * validation;
        return round4(clamp01(confidence));
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
