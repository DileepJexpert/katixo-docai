package com.katixo.ai.eval;

/**
 * Aggregate metrics for one eval run (spec section 8). {@code compositeScore} is the single
 * comparable number used to judge prompt/model changes.
 */
public record EvalMetrics(
        int sampleCount,
        double headerFieldAccuracy,
        double lineItemFieldAccuracy,
        double grandTotalExactMatchRate,
        double exceptionPrecision,
        double exceptionRecall,
        double exceptionF1,
        double meanLatencyMs,
        double pctNeedingReview,
        double compositeScore
) {
}
