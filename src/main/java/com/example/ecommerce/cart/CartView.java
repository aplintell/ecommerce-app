package com.example.ecommerce.cart;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cart as rendered: built from current database prices at request time.
 */
public record CartView(List<CartLine> lines, BigDecimal subtotal, BigDecimal total, int itemCount,
                       List<String> notices, boolean checkoutAllowed) {

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
