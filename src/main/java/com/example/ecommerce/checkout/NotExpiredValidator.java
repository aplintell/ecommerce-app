package com.example.ecommerce.checkout;

import java.time.YearMonth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NotExpiredValidator implements ConstraintValidator<NotExpired, CheckoutForm> {

    private String message;

    @Override
    public void initialize(NotExpired constraintAnnotation) {
        this.message = constraintAnnotation.message();
    }

    @Override
    public boolean isValid(CheckoutForm form, ConstraintValidatorContext context) {
        if (form == null) {
            return true;
        }
        Integer month = form.getExpiryMonth();
        Integer year = form.getExpiryYear();
        // Missing or out-of-range values are reported by the field-level constraints.
        if (month == null || year == null || month < 1 || month > 12) {
            return true;
        }
        YearMonth now = YearMonth.now(context.getClockProvider().getClock());
        boolean valid = !YearMonth.of(year, month).isBefore(now);
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode("expiryMonth")
                    .addConstraintViolation();
        }
        return valid;
    }
}
