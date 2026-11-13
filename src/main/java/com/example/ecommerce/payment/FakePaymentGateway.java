package com.example.ecommerce.payment;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-process sandbox gateway. Approves any Luhn-valid, non-expired card except the decline test numbers.
 */
@Component
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGateway implements PaymentGateway {

    static final String DECLINE_CARD = "4000000000000002";
    static final String INSUFFICIENT_FUNDS_CARD = "4000000000009995";

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    private final Clock clock;

    public FakePaymentGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        CardDetails card = request.card();
        String digits = CardNumbers.digitsOnly(card.number());
        String last4 = CardNumbers.last4(digits);
        CardBrand brand = CardBrand.detect(digits);

        PaymentResult result;
        if (!CardNumbers.luhnValid(digits)) {
            result = PaymentResult.declined("invalid_card", last4, brand);
        } else if (isExpired(card)) {
            result = PaymentResult.declined("expired_card", last4, brand);
        } else if (DECLINE_CARD.equals(digits)) {
            result = PaymentResult.declined("card_declined", last4, brand);
        } else if (INSUFFICIENT_FUNDS_CARD.equals(digits)) {
            result = PaymentResult.declined("insufficient_funds", last4, brand);
        } else {
            result = PaymentResult.approved("fake_" + UUID.randomUUID(), last4, brand);
        }

        String outcome = result.approved() ? "approved" : "declined(" + result.declineCode() + ")";
        log.info("Fake charge {} for order {} card ****{}", outcome, request.orderReference(), last4);
        return result;
    }

    private boolean isExpired(CardDetails card) {
        int month = card.expiryMonth();
        if (month < 1 || month > 12) {
            return true;
        }
        return YearMonth.of(card.expiryYear(), month).isBefore(YearMonth.now(clock));
    }
}
