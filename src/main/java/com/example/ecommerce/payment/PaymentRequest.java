package com.example.ecommerce.payment;

import java.math.BigDecimal;

public record PaymentRequest(String orderReference, BigDecimal amount, String currency, CardDetails card) {
}
