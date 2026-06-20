package com.katixo.ai.validation;

import com.katixo.ai.model.ExtractionException;

import java.util.List;

/**
 * Result of deterministic validation.
 *
 * @param exceptions   field-level problems found (empty == clean)
 * @param passedChecks number of checks that passed
 * @param totalChecks  number of checks actually run (checks are skipped when their inputs are null)
 */
public record ValidationOutcome(List<ExtractionException> exceptions, int passedChecks, int totalChecks) {

    public ValidationOutcome {
        exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
    }

    /** Fraction of run checks that passed; 1.0 when no checks could be run. */
    public double passRatio() {
        return totalChecks == 0 ? 1.0 : (double) passedChecks / totalChecks;
    }
}
