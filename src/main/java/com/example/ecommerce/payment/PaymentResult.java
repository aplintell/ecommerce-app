package com.example.ecommerce.payment;

public record PaymentResult(boolean approved, String transactionId, String declineCode, String cardLast4,
                            CardBrand brand) {

    public static PaymentResult approved(String transactionId, String cardLast4, CardBrand brand) {
        return new PaymentResult(true, transactionId, null, cardLast4, brand);
    }

    public static PaymentResult declined(String declineCode, String cardLast4, CardBrand brand) {
        return new PaymentResult(false, null, declineCode, cardLast4, brand);
    }
}
