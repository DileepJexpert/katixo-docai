package com.katixo.ai.exception;

import com.katixo.ai.model.ModelPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceCalculatorTest {

    private final ConfidenceCalculator calc = new ConfidenceCalculator();

    @Test
    void perfectTextRunIsFullConfidence() {
        double c = calc.compute(1.0, true, false, 1.0, ModelPath.TEXT_LLM);
        assertThat(c).isEqualTo(1.0);
    }

    @Test
    void combinesSignalsWithDocumentedWeights() {
        // 0.4*0.8 + 0.2*1.0 + 0.4*0.5 = 0.72
        double c = calc.compute(0.8, true, false, 0.5, ModelPath.TEXT_LLM);
        assertThat(c).isEqualTo(0.72);
    }

    @Test
    void repairLowersJsonTerm() {
        double notRepaired = calc.compute(1.0, true, false, 1.0, ModelPath.TEXT_LLM);
        double repaired = calc.compute(1.0, true, true, 1.0, ModelPath.TEXT_LLM);
        assertThat(repaired).isLessThan(notRepaired);
    }

    @Test
    void invalidJsonZeroesJsonTerm() {
        // 0.4*1.0 + 0.2*0.0 + 0.4*0.0 = 0.4
        double c = calc.compute(1.0, false, true, 0.0, ModelPath.TEXT_LLM);
        assertThat(c).isEqualTo(0.4);
    }

    @Test
    void vlmPathUsesPriorInsteadOfOcrConfidence() {
        // OCR confidence is ignored on VLM path (prior 0.7 used): 0.4*0.7 + 0.2 + 0.4 = 0.88
        double c = calc.compute(0.0, true, false, 1.0, ModelPath.VLM);
        assertThat(c).isEqualTo(0.88);
    }
}
