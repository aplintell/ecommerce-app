package com.example.ecommerce.payment;

public enum CardBrand {
    VISA,
    MASTERCARD,
    AMEX,
    DISCOVER,
    UNKNOWN;

    /** Detects the brand from the leading digits of a digits-only card number. */
    public static CardBrand detect(String digits) {
        if (digits == null || digits.isEmpty()) {
            return UNKNOWN;
        }
        if (digits.startsWith("4")) {
            return VISA;
        }
        int p2 = prefix(digits, 2);
        int p4 = prefix(digits, 4);
        if ((p2 >= 51 && p2 <= 55) || (p4 >= 2221 && p4 <= 2720)) {
            return MASTERCARD;
        }
        if (p2 == 34 || p2 == 37) {
            return AMEX;
        }
        if (p4 == 6011 || p2 == 65) {
            return DISCOVER;
        }
        return UNKNOWN;
    }

    private static int prefix(String digits, int length) {
        if (digits.length() < length) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.substring(0, length));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
