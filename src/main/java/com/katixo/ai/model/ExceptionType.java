package com.katixo.ai.model;

/**
 * Field-level exception categories surfaced to the human reviewer (spec sections 5.5 & 5.6).
 *
 * <p>These are produced by deterministic Java checks AFTER the LLM has run. The code never silently
 * "fixes" a value - it records exactly what failed so a human knows what to look at.
 */
public enum ExceptionType {
    /** A money/quantity arithmetic check failed (header or line totals do not add up). */
    ARITHMETIC_MISMATCH,
    /** GST tax amount does not match taxableValue x gstRate. */
    TAX_MISMATCH,
    /** GSTIN is missing, malformed, or fails the checksum digit. */
    INVALID_GSTIN,
    /** HSN code is not numeric 4/6/8 digits. */
    INVALID_HSN,
    /** Date is missing or unparseable. */
    INVALID_DATE,
    /** Date parses but is in the future. */
    FUTURE_DATE,
    /** A required field is null/absent. */
    MISSING_FIELD,
    /** Model output was not valid JSON even after one repair attempt. */
    JSON_INVALID,
    /** Model output was valid JSON but did not match the extraction schema. */
    SCHEMA_INVALID,
    /** An upstream dependency (Ollama / OCR) failed mid-pipeline. */
    PIPELINE_ERROR
}
