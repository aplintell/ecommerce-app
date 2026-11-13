package com.example.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class FakePaymentGatewayTest {

    /** Fixed "now": 15 June 2030, UTC. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-15T10:00:00Z"), ZoneOffset.UTC);

    private final FakePaymentGateway gateway = new FakePaymentGateway(CLOCK);

    private static PaymentRequest request(String number, int month, int year) {
        return new PaymentRequest("order-ref-1", new BigDecimal("1234.56"), "USD",
                new CardDetails(number, month, year, "7373", "Jane Doe"));
    }

    @Test
    void shouldApproveTestVisaCard() {
        PaymentResult result = gateway.charge(request("4242424242424242", 12, 2032));

        assertThat(result.approved()).isTrue();
        assertThat(result.transactionId()).startsWith("fake_").hasSize("fake_".length() + 36);
        assertThat(result.declineCode()).isNull();
        assertThat(result.cardLast4()).isEqualTo("4242");
        assertThat(result.brand()).isEqualTo(CardBrand.VISA);
    }

    @Test
    void shouldGenerateDistinctTransactionIds() {
        PaymentResult first = gateway.charge(request("4242424242424242", 12, 2032));
        PaymentResult second = gateway.charge(request("4242424242424242", 12, 2032));
        assertThat(first.transactionId()).isNotEqualTo(second.transactionId());
    }

    @Test
    void shouldApproveCardExpiringThisMonth() {
        assertThat(gateway.charge(request("4242424242424242", 6, 2030)).approved()).isTrue();
    }

    @Test
    void shouldDeclineCardDeclinedTestNumber() {
        PaymentResult result = gateway.charge(request("4000000000000002", 12, 2032));

        assertThat(result.approved()).isFalse();
        assertThat(result.declineCode()).isEqualTo("card_declined");
        assertThat(result.transactionId()).isNull();
        assertThat(result.cardLast4()).isEqualTo("0002");
        assertThat(result.brand()).isEqualTo(CardBrand.VISA);
    }

    @Test
    void shouldDeclineInsufficientFundsTestNumber() {
        PaymentResult result = gateway.charge(request("4000000000009995", 12, 2032));

        assertThat(result.approved()).isFalse();
        assertThat(result.declineCode()).isEqualTo("insufficient_funds");
        assertThat(result.cardLast4()).isEqualTo("9995");
    }

    @Test
    void shouldDeclineLuhnInvalidNumberAsInvalidCard() {
        PaymentResult result = gateway.charge(request("4242424242424241", 12, 2032));

        assertThat(result.approved()).isFalse();
        assertThat(result.declineCode()).isEqualTo("invalid_card");
        assertThat(result.cardLast4()).isEqualTo("4241");
    }

    @Test
    void shouldDeclineExpiredCard() {
        PaymentResult result = gateway.charge(request("4242424242424242", 5, 2030));

        assertThat(result.approved()).isFalse();
        assertThat(result.declineCode()).isEqualTo("expired_card");
    }

    @Test
    void cardDetailsToStringShouldNotContainFullNumberOrCvv() {
        CardDetails card = new CardDetails("4242424242424242", 12, 2032, "7373", "Jane Doe");

        String text = card.toString();

        assertThat(text).doesNotContain("4242424242424242").doesNotContain("7373").contains("4242");
        assertThat(new PaymentRequest("ref", BigDecimal.ONE, "USD", card).toString())
                .doesNotContain("4242424242424242").doesNotContain("7373");
    }
}
