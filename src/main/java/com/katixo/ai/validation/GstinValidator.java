package com.katixo.ai.validation;

import java.util.regex.Pattern;

/**
 * Validates an Indian GSTIN: 15-char format plus the mod-36 checksum digit.
 *
 * <p>Layout: 2-digit state code, 10-char PAN, 1 entity code, 'Z', 1 check digit.
 * The check digit uses the standard GST "modulus 36" algorithm.
 */
public final class GstinValidator {

    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = CHARSET.length(); // 36
    private static final Pattern FORMAT =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    private GstinValidator() {
    }

    public static boolean isValid(String gstin) {
        if (gstin == null) {
            return false;
        }
        String g = gstin.trim().toUpperCase();
        if (g.length() != 15 || !FORMAT.matcher(g).matches()) {
            return false;
        }
        return g.charAt(14) == checkDigit(g.substring(0, 14));
    }

    /** Computes the GSTIN check digit for the first 14 characters. */
    static char checkDigit(String first14) {
        int factor = 2;
        int sum = 0;
        for (int i = first14.length() - 1; i >= 0; i--) {
            int code = CHARSET.indexOf(first14.charAt(i));
            if (code < 0) {
                return '\0'; // illegal char -> guaranteed mismatch
            }
            int product = code * factor;
            factor = (factor == 2) ? 1 : 2;
            product = (product / BASE) + (product % BASE);
            sum += product;
        }
        int remainder = sum % BASE;
        int checkCode = (BASE - remainder) % BASE;
        return CHARSET.charAt(checkCode);
    }
}
