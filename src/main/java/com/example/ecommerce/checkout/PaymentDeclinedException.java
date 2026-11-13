package com.example.ecommerce.checkout;

/**
 * The gateway declined the payment. Unchecked so that the checkout transaction rolls back.
 */
public class PaymentDeclinedException extends RuntimeException {

    private final String declineCode;

    public PaymentDeclinedException(String declineCode) {
        super("Payment declined: " + declineCode);
        this.declineCode = declineCode;
    }

    public String getDeclineCode() {
        return declineCode;
    }
}
