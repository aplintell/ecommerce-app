package com.example.ecommerce.checkout;

import com.example.ecommerce.payment.CardNumbers;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidCardNumberValidator implements ConstraintValidator<ValidCardNumber, String> {

    private static final int MIN_DIGITS = 12;
    private static final int MAX_DIGITS = 19;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= '0' && c <= '9') || c == ' ' || c == '-';
            if (!allowed) {
                return false;
            }
        }
        String digits = CardNumbers.digitsOnly(value);
        return digits.length() >= MIN_DIGITS && digits.length() <= MAX_DIGITS && CardNumbers.luhnValid(digits);
    }
}
