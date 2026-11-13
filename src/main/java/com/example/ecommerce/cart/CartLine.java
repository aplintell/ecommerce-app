package com.example.ecommerce.cart;

import java.math.BigDecimal;

public record CartLine(Long productId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal,
                       int availableStock, boolean insufficientStock) {
}
