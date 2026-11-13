package com.example.ecommerce.payment;

/**
 * Payment provider abstraction. Implementations are selected with {@code app.payment.gateway=<name>}.
 * Implementations must never log card data or put it into exception messages.
 */
public interface PaymentGateway {

    PaymentResult charge(PaymentRequest request);
}
