package com.bookingsquadra.util;

import com.bookingsquadra.exception.UnprocessableEntityException;

/**
 * Normalizes Brazilian phone numbers to E.164 (e.g. {@code +5511987654321}). Accepts common
 * input shapes: with/without country code, with/without punctuation. Rejects anything that
 * can't plausibly be a BR number.
 *
 * <p>This is intentionally Brazil-only and small — it can be swapped for libphonenumber if
 * we ever need to support other countries.
 */
public final class BrazilPhoneNormalizer {

    private static final String COUNTRY_CODE = "55";

    private BrazilPhoneNormalizer() {}

    public static String normalizeOrThrow(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UnprocessableEntityException("invalid_phone", "phone is required");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith(COUNTRY_CODE) && (digits.length() == 12 || digits.length() == 13)) {
            digits = digits.substring(2);
        }
        if (digits.length() != 10 && digits.length() != 11) {
            throw new UnprocessableEntityException("invalid_phone",
                    "phone must be a valid Brazilian number (10 or 11 digits)");
        }
        // Mobile numbers (11 digits) must start with 9 after the 2-digit area code.
        if (digits.length() == 11 && digits.charAt(2) != '9') {
            throw new UnprocessableEntityException("invalid_phone",
                    "mobile numbers must start with 9 after the area code");
        }
        return "+" + COUNTRY_CODE + digits;
    }
}
