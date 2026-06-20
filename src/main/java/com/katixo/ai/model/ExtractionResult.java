package com.katixo.ai.model;

import java.util.List;

/**
 * The public extraction contract returned by {@code POST /api/v1/extract} (spec section 5.4).
 */
public record ExtractionResult(
        String id,
        DocType docType,
        ModelPath modelPath,
        InvoiceHeader header,
        List<LineItem> lineItems,
        double confidence,
        List<ExtractionException> exceptions,
        boolean needsHumanReview
) {
    public ExtractionResult {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
    }
}
