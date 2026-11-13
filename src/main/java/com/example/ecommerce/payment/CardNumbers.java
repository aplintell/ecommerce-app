package com.example.ecommerce.payment;

/**
 * Helpers for card numbers. None of these methods log or retain their input.
 */
public final class CardNumbers {

    private CardNumbers() {
    }

    /** Removes everything except ASCII digits. Returns an empty string for null. */
    public static String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Luhn (mod 10) check on a digits-only string. Empty or non-digit input is invalid. */
    public static boolean luhnValid(String digits) {
        if (digits == null || digits.isEmpty()) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int d = c - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Last 4 digits of the number (digits only), or all digits if fewer than 4. */
    public static String last4(String number) {
        String digits = digitsOnly(number);
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    /** Masked form such as {@code **** 4242}. */
    public static String mask(String number) {
        return "**** " + last4(number);
    }
}
