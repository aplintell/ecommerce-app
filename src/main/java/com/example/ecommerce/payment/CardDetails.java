package com.example.ecommerce.payment;

/**
 * Card data passed to a gateway. Never persisted or logged; {@link #toString()} is masked.
 *
 * @param number digits only
 */
public record CardDetails(String number, int expiryMonth, int expiryYear, String cvv, String holderName) {

    @Override
    public String toString() {
        return "CardDetails[" + CardNumbers.mask(number) + "]";
    }
}
