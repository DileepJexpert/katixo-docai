package com.katixo.ai.model;

/**
 * A single line on the document. As with the header, absent values are {@code null}, not zero.
 *
 * @param gstRate GST rate as a percentage (e.g. {@code 18.0} means 18%)
 */
public record LineItem(
        String description,
        String hsn,
        Double qty,
        String uom,
        Double rate,
        Double discount,
        Double taxableValue,
        Double gstRate,
        Double lineTotal
) {
}
