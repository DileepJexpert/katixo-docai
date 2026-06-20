package com.katixo.ai.validation;

import com.katixo.ai.model.ExceptionType;
import com.katixo.ai.model.ExtractedDocument;
import com.katixo.ai.model.ExtractionException;
import com.katixo.ai.model.InvoiceHeader;
import com.katixo.ai.model.LineItem;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic validation - the moat (spec section 5.5). Runs in plain Java AFTER the LLM:
 * the model extracts, this code checks the maths. Failures become field-level exceptions; numbers
 * are NEVER silently "fixed".
 */
@Service
public class ValidationService {

    /** Money/quantity tolerance in rupees. */
    private static final double MONEY_TOLERANCE = 1.0;
    private static final Pattern HSN_PATTERN = Pattern.compile("\\d{4}|\\d{6}|\\d{8}");

    private final Clock clock;

    public ValidationService(Clock clock) {
        this.clock = clock;
    }

    public ValidationOutcome validate(ExtractedDocument doc) {
        Checks checks = new Checks();
        InvoiceHeader h = doc == null ? null : doc.header();

        if (h == null) {
            checks.fail(ExtractionException.of("header", ExceptionType.MISSING_FIELD,
                    "No header was extracted."));
            return checks.toOutcome();
        }

        validateGstin(h, checks);
        validateDate(h, checks);
        validateSubTotal(doc, checks);
        validateGrandTotal(h, checks);
        validateLineItems(doc.lineItems(), checks);

        return checks.toOutcome();
    }

    private void validateGstin(InvoiceHeader h, Checks checks) {
        if (h.supplierGstin() == null || h.supplierGstin().isBlank()) {
            checks.fail(ExtractionException.of("supplierGstin", ExceptionType.MISSING_FIELD,
                    "Supplier GSTIN is missing."));
            return;
        }
        if (GstinValidator.isValid(h.supplierGstin())) {
            checks.pass();
        } else {
            checks.fail(ExtractionException.of("supplierGstin", ExceptionType.INVALID_GSTIN,
                    "GSTIN '" + h.supplierGstin() + "' fails format/checksum validation."));
        }
    }

    private void validateDate(InvoiceHeader h, Checks checks) {
        if (h.invoiceDate() == null || h.invoiceDate().isBlank()) {
            checks.fail(ExtractionException.of("invoiceDate", ExceptionType.MISSING_FIELD,
                    "Invoice date is missing."));
            return;
        }
        try {
            LocalDate date = LocalDate.parse(h.invoiceDate().trim());
            if (date.isAfter(LocalDate.now(clock))) {
                checks.fail(ExtractionException.of("invoiceDate", ExceptionType.FUTURE_DATE,
                        "Invoice date " + date + " is in the future."));
            } else {
                checks.pass();
            }
        } catch (DateTimeParseException e) {
            checks.fail(ExtractionException.of("invoiceDate", ExceptionType.INVALID_DATE,
                    "Invoice date '" + h.invoiceDate() + "' is not a valid YYYY-MM-DD date."));
        }
    }

    /** Sum(lineItems.taxableValue) ~= subTotal. */
    private void validateSubTotal(ExtractedDocument doc, Checks checks) {
        InvoiceHeader h = doc.header();
        if (h.subTotal() == null) {
            return; // nothing to check against
        }
        Double sum = sumTaxable(doc.lineItems());
        if (sum == null) {
            return; // no line taxable values present
        }
        if (approx(sum, h.subTotal())) {
            checks.pass();
        } else {
            checks.fail(ExtractionException.of("subTotal", ExceptionType.ARITHMETIC_MISMATCH,
                    "Sum of line taxable values (" + round(sum) + ") != subTotal (" + h.subTotal() + ")."));
        }
    }

    /** subTotal + cgst + sgst + igst + roundOff ~= grandTotal. */
    private void validateGrandTotal(InvoiceHeader h, Checks checks) {
        if (h.grandTotal() == null || h.subTotal() == null) {
            return;
        }
        double computed = h.subTotal() + nz(h.cgst()) + nz(h.sgst()) + nz(h.igst()) + nz(h.roundOff());
        if (approx(computed, h.grandTotal())) {
            checks.pass();
        } else {
            checks.fail(ExtractionException.of("grandTotal", ExceptionType.ARITHMETIC_MISMATCH,
                    "subTotal+taxes+roundOff (" + round(computed) + ") != grandTotal (" + h.grandTotal() + ")."));
        }
    }

    private void validateLineItems(List<LineItem> items, Checks checks) {
        for (int i = 0; i < items.size(); i++) {
            LineItem li = items.get(i);
            String prefix = "lineItems[" + i + "]";

            // qty * rate - discount ~= taxableValue
            if (li.qty() != null && li.rate() != null && li.taxableValue() != null) {
                double computed = li.qty() * li.rate() - nz(li.discount());
                if (approx(computed, li.taxableValue())) {
                    checks.pass();
                } else {
                    checks.fail(ExtractionException.of(prefix + ".taxableValue", ExceptionType.ARITHMETIC_MISMATCH,
                            "qty*rate-discount (" + round(computed) + ") != taxableValue (" + li.taxableValue() + ")."));
                }
            }

            // taxableValue * (1 + gstRate/100) ~= lineTotal  (validates the tax amount implicitly)
            if (li.taxableValue() != null && li.gstRate() != null && li.lineTotal() != null) {
                double computed = li.taxableValue() * (1.0 + li.gstRate() / 100.0);
                double tolerance = Math.max(MONEY_TOLERANCE, Math.abs(computed) * 0.01);
                if (Math.abs(computed - li.lineTotal()) <= tolerance) {
                    checks.pass();
                } else {
                    checks.fail(ExtractionException.of(prefix + ".lineTotal", ExceptionType.TAX_MISMATCH,
                            "taxableValue+GST (" + round(computed) + ") != lineTotal (" + li.lineTotal() + ")."));
                }
            }

            // HSN format (only when present)
            if (li.hsn() != null && !li.hsn().isBlank()) {
                if (HSN_PATTERN.matcher(li.hsn().trim()).matches()) {
                    checks.pass();
                } else {
                    checks.fail(ExtractionException.of(prefix + ".hsn", ExceptionType.INVALID_HSN,
                            "HSN '" + li.hsn() + "' is not numeric 4/6/8 digits."));
                }
            }
        }
    }

    // --- helpers ---

    private static Double sumTaxable(List<LineItem> items) {
        boolean any = false;
        double sum = 0;
        for (LineItem li : items) {
            if (li.taxableValue() != null) {
                sum += li.taxableValue();
                any = true;
            }
        }
        return any ? sum : null;
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private static boolean approx(double a, double b) {
        return Math.abs(a - b) <= MONEY_TOLERANCE;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Accumulates pass/fail counts alongside the exception list. */
    private static final class Checks {
        private final List<ExtractionException> exceptions = new ArrayList<>();
        private int passed = 0;
        private int total = 0;

        void pass() {
            total++;
            passed++;
        }

        void fail(ExtractionException e) {
            total++;
            exceptions.add(e);
        }

        ValidationOutcome toOutcome() {
            return new ValidationOutcome(exceptions, passed, total);
        }
    }
}
