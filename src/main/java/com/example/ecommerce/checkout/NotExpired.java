package com.example.ecommerce.checkout;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Class-level constraint on {@link CheckoutForm}: the card expiry month/year must not be in the past,
 * judged with the Bean Validation {@link jakarta.validation.ClockProvider}. The violation is reported on
 * the {@code expiryMonth} property.
 */
@Documented
@Constraint(validatedBy = NotExpiredValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotExpired {

    String message() default "Card has expired";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
