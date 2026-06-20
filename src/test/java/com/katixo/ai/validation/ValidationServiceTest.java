package com.katixo.ai.validation;

import com.katixo.ai.model.DocType;
import com.katixo.ai.model.ExceptionType;
import com.katixo.ai.model.ExtractedDocument;
import com.katixo.ai.model.ExtractionException;
import com.katixo.ai.model.InvoiceHeader;
import com.katixo.ai.model.LineItem;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationServiceTest {

    // Fixed "now" comfortably after the sample invoice dates.
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T00:00:00Z"), ZoneOffset.UTC);
    private final ValidationService service = new ValidationService(clock);

    @Test
    void cleanInvoicePassesEveryCheck() {
        ValidationOutcome outcome = service.validate(cleanDoc());
        assertThat(outcome.exceptions()).isEmpty();
        assertThat(outcome.totalChecks()).isGreaterThan(0);
        assertThat(outcome.passedChecks()).isEqualTo(outcome.totalChecks());
        assertThat(outcome.passRatio()).isEqualTo(1.0);
    }

    @Test
    void grandTotalMismatchIsAnException() {
        InvoiceHeader h = withGrandTotal(cleanHeader(), 99999.0);
        ValidationOutcome outcome = service.validate(new ExtractedDocument(DocType.INVOICE, h, cleanLines()));
        assertThat(typesOf(outcome)).contains(ExceptionType.ARITHMETIC_MISMATCH);
        assertThat(fieldsOf(outcome)).contains("grandTotal");
    }

    @Test
    void invalidGstinIsAnException() {
        InvoiceHeader h = withGstin(cleanHeader(), "29ABCDE1234F1ZX"); // wrong checksum
        ValidationOutcome outcome = service.validate(new ExtractedDocument(DocType.INVOICE, h, cleanLines()));
        assertThat(typesOf(outcome)).contains(ExceptionType.INVALID_GSTIN);
    }

    @Test
    void missingGstinIsMissingField() {
        InvoiceHeader h = withGstin(cleanHeader(), null);
        ValidationOutcome outcome = service.validate(new ExtractedDocument(DocType.INVOICE, h, cleanLines()));
        assertThat(typesOf(outcome)).contains(ExceptionType.MISSING_FIELD);
    }

    @Test
    void futureDateIsAnException() {
        InvoiceHeader h = withDate(cleanHeader(), "2027-01-01");
        ValidationOutcome outcome = service.validate(new ExtractedDocument(DocType.INVOICE, h, cleanLines()));
        assertThat(typesOf(outcome)).contains(ExceptionType.FUTURE_DATE);
    }

    @Test
    void unparseableDateIsInvalidDate() {
        InvoiceHeader h = withDate(cleanHeader(), "14/05/2024"); // not ISO
        ValidationOutcome outcome = service.validate(new ExtractedDocument(DocType.INVOICE, h, cleanLines()));
        assertThat(typesOf(outcome)).contains(ExceptionType.INVALID_DATE);
    }

    @Test
    void badHsnIsAnException() {
        LineItem bad = new LineItem("Paracetamol 500mg", "30", 100.0, "BOX", 45.0, 0.0, 4500.0, 12.0, 5040.0);
        LineItem ok = cleanLines().get(1);
        ValidationOutcome outcome = service.validate(new ExtractedDocument(DocType.INVOICE, cleanHeader(), List.of(bad, ok)));
        assertThat(typesOf(outcome)).contains(ExceptionType.INVALID_HSN);
    }

    @Test
    void lineArithmeticMismatchIsAnException() {
        // taxableValue should be 4500 (100*45); say 9999 instead.
        LineItem bad = new LineItem("Paracetamol 500mg", "3004", 100.0, "BOX", 45.0, 0.0, 9999.0, 12.0, 5040.0);
        ValidationOutcome outcome = service.validate(new ExtractedDocument(DocType.INVOICE, cleanHeader(), List.of(bad)));
        assertThat(typesOf(outcome)).contains(ExceptionType.ARITHMETIC_MISMATCH);
    }

    @Test
    void nullHeaderIsMissingFieldNotCrash() {
        ValidationOutcome outcome = service.validate(new ExtractedDocument(DocType.INVOICE, null, List.of()));
        assertThat(typesOf(outcome)).contains(ExceptionType.MISSING_FIELD);
    }

    // --- fixtures ---

    private static ExtractedDocument cleanDoc() {
        return new ExtractedDocument(DocType.INVOICE, cleanHeader(), cleanLines());
    }

    private static InvoiceHeader cleanHeader() {
        return new InvoiceHeader("Sri Balaji Pharma Distributors", "29ABCDE1234F1ZW", "SBP/24-25/00187",
                "2024-05-14", 8950.0, 537.0, 537.0, null, 0.0, 10024.0, "INR");
    }

    private static List<LineItem> cleanLines() {
        return List.of(
                new LineItem("Paracetamol 500mg", "3004", 100.0, "BOX", 45.0, 0.0, 4500.0, 12.0, 5040.0),
                new LineItem("Amoxicillin 250mg", "3004", 50.0, "BOX", 90.0, 50.0, 4450.0, 12.0, 4984.0));
    }

    private static InvoiceHeader withGrandTotal(InvoiceHeader h, Double v) {
        return new InvoiceHeader(h.supplierName(), h.supplierGstin(), h.invoiceNumber(), h.invoiceDate(),
                h.subTotal(), h.cgst(), h.sgst(), h.igst(), h.roundOff(), v, h.currency());
    }

    private static InvoiceHeader withGstin(InvoiceHeader h, String v) {
        return new InvoiceHeader(h.supplierName(), v, h.invoiceNumber(), h.invoiceDate(),
                h.subTotal(), h.cgst(), h.sgst(), h.igst(), h.roundOff(), h.grandTotal(), h.currency());
    }

    private static InvoiceHeader withDate(InvoiceHeader h, String v) {
        return new InvoiceHeader(h.supplierName(), h.supplierGstin(), h.invoiceNumber(), v,
                h.subTotal(), h.cgst(), h.sgst(), h.igst(), h.roundOff(), h.grandTotal(), h.currency());
    }

    private static List<ExceptionType> typesOf(ValidationOutcome o) {
        return o.exceptions().stream().map(ExtractionException::type).toList();
    }

    private static List<String> fieldsOf(ValidationOutcome o) {
        return o.exceptions().stream().map(ExtractionException::field).toList();
    }
}
