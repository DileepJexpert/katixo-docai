package com.katixo.ai.eval;

import com.katixo.ai.model.ExtractionException;
import com.katixo.ai.model.ExtractionResult;
import com.katixo.ai.model.InvoiceHeader;
import com.katixo.ai.model.LineItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Pure scoring logic for the eval harness (spec section 8). No IO, no services - so it is fully
 * unit-testable and the metric definitions are pinned down.
 *
 * <p>Field accuracy is measured over the fields the ground truth actually contains (non-null),
 * rewarding correctly extracting present fields. Money/quantity matches use a small tolerance.
 */
public class EvalHarness {

    private static final double VALUE_TOLERANCE = 0.5;

    // Composite weighting (documented so changes are deliberate).
    private static final double W_HEADER = 0.35;
    private static final double W_LINES = 0.25;
    private static final double W_GRAND = 0.25;
    private static final double W_EXC_F1 = 0.15;

    public EvalCaseResult compare(String name, EvalExpected expected, ExtractionResult actual, long latencyMs) {
        int[] header = compareHeader(expected.header(), actual.header());
        int[] lines = compareLineItems(expected.lineItems(), actual.lineItems());
        boolean grand = numbersMatch(
                expected.header() == null ? null : expected.header().grandTotal(),
                actual.header() == null ? null : actual.header().grandTotal());
        int[] exc = compareExceptions(expected.expectedExceptions(), actual.exceptions());

        return new EvalCaseResult(name, header[0], header[1], lines[0], lines[1], grand,
                exc[0], exc[1], exc[2], actual.needsHumanReview(), latencyMs);
    }

    public EvalMetrics aggregate(List<EvalCaseResult> cases) {
        if (cases.isEmpty()) {
            return new EvalMetrics(0, 1, 1, 1, 1, 1, 1, 0, 0, 1);
        }
        int hm = 0, ht = 0, lm = 0, lt = 0, grand = 0, tp = 0, fp = 0, fn = 0, review = 0;
        double latency = 0;
        for (EvalCaseResult c : cases) {
            hm += c.headerFieldsMatched();
            ht += c.headerFieldsTotal();
            lm += c.lineFieldsMatched();
            lt += c.lineFieldsTotal();
            grand += c.grandTotalMatch() ? 1 : 0;
            tp += c.exceptionTruePositives();
            fp += c.exceptionFalsePositives();
            fn += c.exceptionFalseNegatives();
            review += c.needsReview() ? 1 : 0;
            latency += c.latencyMs();
        }
        int n = cases.size();
        double headerAcc = ht == 0 ? 1.0 : (double) hm / ht;
        double lineAcc = lt == 0 ? 1.0 : (double) lm / lt;
        double grandRate = (double) grand / n;
        double precision = (tp + fp) == 0 ? 1.0 : (double) tp / (tp + fp);
        double recall = (tp + fn) == 0 ? 1.0 : (double) tp / (tp + fn);
        double f1 = (precision + recall) == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
        double meanLatency = latency / n;
        double pctReview = (double) review / n;
        double composite = W_HEADER * headerAcc + W_LINES * lineAcc + W_GRAND * grandRate + W_EXC_F1 * f1;

        return new EvalMetrics(n, round4(headerAcc), round4(lineAcc), round4(grandRate),
                round4(precision), round4(recall), round4(f1), round1(meanLatency),
                round4(pctReview), round4(composite));
    }

    // --- field comparison ---

    /** @return [matched, total] over non-null ground-truth header fields. */
    int[] compareHeader(InvoiceHeader exp, InvoiceHeader act) {
        int[] acc = new int[2];
        if (exp == null) {
            return acc;
        }
        str(acc, exp.supplierName(), act == null ? null : act.supplierName());
        str(acc, exp.supplierGstin(), act == null ? null : act.supplierGstin());
        str(acc, exp.invoiceNumber(), act == null ? null : act.invoiceNumber());
        str(acc, exp.invoiceDate(), act == null ? null : act.invoiceDate());
        num(acc, exp.subTotal(), act == null ? null : act.subTotal());
        num(acc, exp.cgst(), act == null ? null : act.cgst());
        num(acc, exp.sgst(), act == null ? null : act.sgst());
        num(acc, exp.igst(), act == null ? null : act.igst());
        num(acc, exp.roundOff(), act == null ? null : act.roundOff());
        num(acc, exp.grandTotal(), act == null ? null : act.grandTotal());
        str(acc, exp.currency(), act == null ? null : act.currency());
        return acc;
    }

    /** @return [matched, total] over non-null ground-truth line-item fields (aligned by index). */
    int[] compareLineItems(List<LineItem> exp, List<LineItem> act) {
        int[] acc = new int[2];
        for (int i = 0; i < exp.size(); i++) {
            LineItem e = exp.get(i);
            LineItem a = i < act.size() ? act.get(i) : null;
            str(acc, e.description(), a == null ? null : a.description());
            str(acc, e.hsn(), a == null ? null : a.hsn());
            num(acc, e.qty(), a == null ? null : a.qty());
            str(acc, e.uom(), a == null ? null : a.uom());
            num(acc, e.rate(), a == null ? null : a.rate());
            num(acc, e.discount(), a == null ? null : a.discount());
            num(acc, e.taxableValue(), a == null ? null : a.taxableValue());
            num(acc, e.gstRate(), a == null ? null : a.gstRate());
            num(acc, e.lineTotal(), a == null ? null : a.lineTotal());
        }
        return acc;
    }

    /** @return [truePositives, falsePositives, falseNegatives]. */
    int[] compareExceptions(List<ExpectedException> expected, List<ExtractionException> predicted) {
        Set<String> exp = toKeys(expected, ExpectedException::key);
        Set<String> pred = toKeys(predicted, e -> e.field() + "|" + e.type());
        int tp = 0;
        for (String k : pred) {
            if (exp.contains(k)) {
                tp++;
            }
        }
        int fp = pred.size() - tp;
        int fn = exp.size() - tp;
        return new int[]{tp, fp, fn};
    }

    private <T> Set<String> toKeys(List<T> items, Function<T, String> keyFn) {
        Set<String> keys = new HashSet<>();
        for (T t : items) {
            keys.add(keyFn.apply(t));
        }
        return keys;
    }

    // accumulate into acc = [matched, total], counting only fields present in ground truth
    private void str(int[] acc, String exp, String act) {
        if (exp == null || exp.isBlank()) {
            return;
        }
        acc[1]++;
        if (normalize(exp).equals(normalize(act))) {
            acc[0]++;
        }
    }

    private void num(int[] acc, Double exp, Double act) {
        if (exp == null) {
            return;
        }
        acc[1]++;
        if (numbersMatch(exp, act)) {
            acc[0]++;
        }
    }

    private static boolean numbersMatch(Double exp, Double act) {
        if (exp == null) {
            return act == null;
        }
        if (act == null) {
            return false;
        }
        return Math.abs(exp - act) <= VALUE_TOLERANCE;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
