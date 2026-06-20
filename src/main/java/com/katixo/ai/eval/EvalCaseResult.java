package com.katixo.ai.eval;

/** Per-document scoring breakdown. */
public record EvalCaseResult(
        String name,
        int headerFieldsMatched,
        int headerFieldsTotal,
        int lineFieldsMatched,
        int lineFieldsTotal,
        boolean grandTotalMatch,
        int exceptionTruePositives,
        int exceptionFalsePositives,
        int exceptionFalseNegatives,
        boolean needsReview,
        long latencyMs
) {
    public double headerAccuracy() {
        return headerFieldsTotal == 0 ? 1.0 : (double) headerFieldsMatched / headerFieldsTotal;
    }

    public double lineAccuracy() {
        return lineFieldsTotal == 0 ? 1.0 : (double) lineFieldsMatched / lineFieldsTotal;
    }
}
