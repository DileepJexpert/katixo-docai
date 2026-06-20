package com.katixo.ai.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GstinValidatorTest {

    @Test
    void acceptsValidGstinsWithCorrectChecksum() {
        // These are the checksum-correct GSTINs emitted by the golden generator.
        assertThat(GstinValidator.isValid("29ABCDE1234F1ZW")).isTrue();
        assertThat(GstinValidator.isValid("27PQRST5678K1Z2")).isTrue();
        assertThat(GstinValidator.isValid("06LMNOP9012Q1Z2")).isTrue();
        assertThat(GstinValidator.isValid("33UVWXY3456R1Z8")).isTrue();
    }

    @Test
    void rejectsWrongChecksum() {
        // Flip the final check digit.
        assertThat(GstinValidator.isValid("29ABCDE1234F1ZX")).isFalse();
    }

    @Test
    void rejectsBadFormatAndNulls() {
        assertThat(GstinValidator.isValid(null)).isFalse();
        assertThat(GstinValidator.isValid("")).isFalse();
        assertThat(GstinValidator.isValid("29ABCDE1234F1Z")).isFalse();      // 14 chars
        assertThat(GstinValidator.isValid("29ABCDE1234F1ZWW")).isFalse();    // 16 chars
        assertThat(GstinValidator.isValid("ZZABCDE1234F1ZW")).isFalse();     // non-numeric state
        assertThat(GstinValidator.isValid("29abcde1234f1zw")).isTrue();      // case-insensitive
    }
}
