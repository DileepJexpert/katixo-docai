package com.katixo.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The structured document as produced by the LLM (the JSON-schema target). The service then wraps
 * this with id / modelPath / confidence / exceptions to form the public {@link ExtractionResult}.
 *
 * <p>{@code docType} may be null here; the pipeline falls back to the caller-supplied hint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedDocument(
        DocType docType,
        InvoiceHeader header,
        List<LineItem> lineItems
) {
    public ExtractedDocument {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }
}
