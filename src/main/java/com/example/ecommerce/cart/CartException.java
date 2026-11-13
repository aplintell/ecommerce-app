package com.example.ecommerce.cart;

/**
 * Business rule violation when changing the cart (insufficient stock, over the 99 cap, invalid quantity).
 * The message is user-facing.
 */
public class CartException extends RuntimeException {

    public CartException(String message) {
        super(message);
    }
}
