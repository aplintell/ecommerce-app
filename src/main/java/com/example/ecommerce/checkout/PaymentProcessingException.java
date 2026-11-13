package com.example.ecommerce.checkout;

/**
 * Unexpected gateway failure. Deliberately carries no cause and a fixed message, because a provider
 * exception could contain card data.
 */
public class PaymentProcessingException extends RuntimeException {

    public PaymentProcessingException() {
        super("Payment processing failed", null, false, false);
    }
}
