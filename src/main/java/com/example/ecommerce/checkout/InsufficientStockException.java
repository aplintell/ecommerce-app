package com.example.ecommerce.checkout;

/**
 * Stock is lower than the requested quantity (or the product is gone). The message is user-facing.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
