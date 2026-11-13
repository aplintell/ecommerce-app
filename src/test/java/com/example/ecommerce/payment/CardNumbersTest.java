package com.example.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CardNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"4242424242424242", "4000000000000002", "4000000000009995", "5555555555554444",
            "378282246310005", "6011111111111117"})
    void shouldAcceptLuhnValidNumbers(String digits) {
        assertThat(CardNumbers.luhnValid(digits)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4242424242424241", "4000000000000001", "1234567890123456", "", "4242a42424242424"})
    void shouldRejectLuhnInvalidOrNonDigitInput(String digits) {
        assertThat(CardNumbers.luhnValid(digits)).isFalse();
    }

    @Test
    void shouldRejectNullForLuhn() {
        assertThat(CardNumbers.luhnValid(null)).isFalse();
    }

    @Test
    void shouldStripSpacesAndDashes() {
        assertThat(CardNumbers.digitsOnly("4242 4242-4242 4242")).isEqualTo("4242424242424242");
        assertThat(CardNumbers.digitsOnly(" 4000-0000-0000-0002 ")).isEqualTo("4000000000000002");
        assertThat(CardNumbers.digitsOnly(null)).isEmpty();
    }

    @Test
    void shouldReturnLastFourDigits() {
        assertThat(CardNumbers.last4("4242 4242 4242 4242")).isEqualTo("4242");
        assertThat(CardNumbers.last4("4000-0000-0000-0002")).isEqualTo("0002");
        assertThat(CardNumbers.last4("12")).isEqualTo("12");
    }

    @Test
    void shouldMaskAllButLastFourDigits() {
        String masked = CardNumbers.mask("4242 4242 4242 4242");
        assertThat(masked).isEqualTo("**** 4242");
        assertThat(masked).doesNotContain("42424242");
    }
}
