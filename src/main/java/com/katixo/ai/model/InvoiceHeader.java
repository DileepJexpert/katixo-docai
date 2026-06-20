package com.katixo.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Invoice/GRN/bill header. Boxed numeric types are intentional: a value that is not present on the
 * document must be {@code null}, never a guessed {@code 0.0} (spec section 5.3 - "use null for
 * anything not present").
 *
 * @param invoiceDate ISO date string {@code YYYY-MM-DD} (kept as String so an unparseable value can
 *                    still round-trip and be flagged, rather than blowing up deserialization)
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record InvoiceHeader(
        String supplierName,
        String supplierGstin,
        String invoiceNumber,
        String invoiceDate,
        Double subTotal,
        Double cgst,
        Double sgst,
        Double igst,
        Double roundOff,
        Double grandTotal,
        String currency
) {
}
