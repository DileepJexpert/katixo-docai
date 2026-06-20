package com.katixo.ai.eval;

import com.katixo.ai.model.DocType;
import com.katixo.ai.model.ExceptionType;
import com.katixo.ai.model.ExtractionException;
import com.katixo.ai.model.ExtractionResult;
import com.katixo.ai.model.InvoiceHeader;
import com.katixo.ai.model.LineItem;
import com.katixo.ai.model.ModelPath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EvalHarnessTest {

    private final EvalHarness harness = new EvalHarness();

    @Test
    void perfectExtractionScoresOneAcrossTheBoard() {
        EvalExpected expected = expected();
        ExtractionResult actual = result(expected.header(), expected.lineItems(), List.of());

        EvalCaseResult c = harness.compare("s1", expected, actual, 1234);
        assertThat(c.headerAccuracy()).isEqualTo(1.0);
        assertThat(c.lineAccuracy()).isEqualTo(1.0);
        assertThat(c.grandTotalMatch()).isTrue();
        assertThat(c.exceptionFalsePositives()).isZero();

        EvalMetrics m = harness.aggregate(List.of(c));
        assertThat(m.headerFieldAccuracy()).isEqualTo(1.0);
        assertThat(m.grandTotalExactMatchRate()).isEqualTo(1.0);
        assertThat(m.compositeScore()).isEqualTo(1.0);
        assertThat(m.meanLatencyMs()).isEqualTo(1234.0);
    }

    @Test
    void wrongGrandTotalAndFieldLowerTheScore() {
        EvalExpected expected = expected();
        InvoiceHeader wrong = new InvoiceHeader(expected.header().supplierName(), expected.header().supplierGstin(),
                "WRONG-NUMBER", expected.header().invoiceDate(), expected.header().subTotal(),
                expected.header().cgst(), expected.header().sgst(), expected.header().igst(),
                expected.header().roundOff(), 99999.0, expected.header().currency());
        ExtractionResult actual = result(wrong, expected.lineItems(), List.of());

        EvalCaseResult c = harness.compare("s1", expected, actual, 10);
        assertThat(c.headerAccuracy()).isLessThan(1.0);
        assertThat(c.grandTotalMatch()).isFalse();
    }

    @Test
    void spuriousExceptionHurtsPrecision() {
        EvalExpected expected = expected(); // expects NO exceptions
        ExtractionResult actual = result(expected.header(), expected.lineItems(),
                List.of(ExtractionException.of("grandTotal", ExceptionType.ARITHMETIC_MISMATCH, "x")));

        EvalCaseResult c = harness.compare("s1", expected, actual, 10);
        assertThat(c.exceptionFalsePositives()).isEqualTo(1);

        EvalMetrics m = harness.aggregate(List.of(c));
        assertThat(m.exceptionPrecision()).isLessThan(1.0);
        assertThat(m.exceptionRecall()).isEqualTo(1.0); // nothing was expected, nothing missed
    }

    @Test
    void matchingExpectedExceptionCountsAsTruePositive() {
        EvalExpected expected = new EvalExpected(DocType.INVOICE, header(), lines(),
                List.of(new ExpectedException("grandTotal", ExceptionType.ARITHMETIC_MISMATCH)));
        ExtractionResult actual = result(header(), lines(),
                List.of(ExtractionException.of("grandTotal", ExceptionType.ARITHMETIC_MISMATCH, "detail")));

        EvalCaseResult c = harness.compare("s1", expected, actual, 10);
        assertThat(c.exceptionTruePositives()).isEqualTo(1);
        assertThat(c.exceptionFalsePositives()).isZero();
        assertThat(c.exceptionFalseNegatives()).isZero();

        EvalMetrics m = harness.aggregate(List.of(c));
        assertThat(m.exceptionF1()).isCloseTo(1.0, within(1e-9));
    }

    // --- fixtures ---

    private static EvalExpected expected() {
        return new EvalExpected(DocType.INVOICE, header(), lines(), List.of());
    }

    private static InvoiceHeader header() {
        return new InvoiceHeader("Sri Balaji Pharma Distributors", "29ABCDE1234F1ZW", "SBP/24-25/00187",
                "2024-05-14", 8950.0, 537.0, 537.0, null, 0.0, 10024.0, "INR");
    }

    private static List<LineItem> lines() {
        return List.of(new LineItem("Paracetamol 500mg", "3004", 100.0, "BOX", 45.0, 0.0, 4500.0, 12.0, 5040.0));
    }

    private static ExtractionResult result(InvoiceHeader h, List<LineItem> items, List<ExtractionException> exc) {
        return new ExtractionResult("id", DocType.INVOICE, ModelPath.TEXT_LLM, h, items, 0.9, exc, !exc.isEmpty());
    }
}
