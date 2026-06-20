package com.katixo.ai.eval;

import java.util.Locale;

/** Formats {@link EvalMetrics} as a single comparable, human-readable report block. */
public final class EvalReport {

    private EvalReport() {
    }

    public static String format(EvalMetrics m) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n====================  KATIXO EVAL REPORT  ====================\n");
        sb.append(String.format(Locale.ROOT, "  samples ................... %d%n", m.sampleCount()));
        sb.append(String.format(Locale.ROOT, "  header field accuracy ..... %.1f%%%n", m.headerFieldAccuracy() * 100));
        sb.append(String.format(Locale.ROOT, "  line-item field accuracy .. %.1f%%%n", m.lineItemFieldAccuracy() * 100));
        sb.append(String.format(Locale.ROOT, "  grand-total exact match ... %.1f%%%n", m.grandTotalExactMatchRate() * 100));
        sb.append(String.format(Locale.ROOT, "  exception precision ....... %.1f%%%n", m.exceptionPrecision() * 100));
        sb.append(String.format(Locale.ROOT, "  exception recall .......... %.1f%%%n", m.exceptionRecall() * 100));
        sb.append(String.format(Locale.ROOT, "  exception F1 .............. %.1f%%%n", m.exceptionF1() * 100));
        sb.append(String.format(Locale.ROOT, "  mean latency .............. %.1f ms%n", m.meanLatencyMs()));
        sb.append(String.format(Locale.ROOT, "  needing human review ...... %.1f%%%n", m.pctNeedingReview() * 100));
        sb.append("  ----------------------------------------------------------\n");
        sb.append(String.format(Locale.ROOT, "  COMPOSITE SCORE ........... %.4f%n", m.compositeScore()));
        sb.append("==============================================================\n");
        return sb.toString();
    }
}
