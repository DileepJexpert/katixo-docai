package com.katixo.ai.model;

/**
 * One field-level reason a human should look at this document.
 *
 * @param field  the field this concerns, e.g. {@code grandTotal} or {@code lineItems[2].taxableValue}
 * @param type   machine-readable category
 * @param detail human-readable explanation (include the numbers that disagreed)
 */
public record ExtractionException(String field, ExceptionType type, String detail) {

    public static ExtractionException of(String field, ExceptionType type, String detail) {
        return new ExtractionException(field, type, detail);
    }
}
