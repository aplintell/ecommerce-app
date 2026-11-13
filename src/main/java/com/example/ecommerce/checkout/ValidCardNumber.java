package com.example.ecommerce.checkout;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Card number made of digits, spaces and dashes only; 12 to 19 digits after stripping; Luhn-valid.
 * Null or blank values are considered valid (use {@code @NotBlank} for that).
 */
@Documented
@Constraint(validatedBy = ValidCardNumberValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCardNumber {

    String message() default "Card number is invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
