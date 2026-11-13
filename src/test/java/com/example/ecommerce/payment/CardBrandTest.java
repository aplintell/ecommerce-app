package com.example.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class CardBrandTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "4242424242424242, VISA",
            "4000000000000002, VISA",
            "5105105105105100, MASTERCARD",
            "5555555555554444, MASTERCARD",
            "2221000000000009, MASTERCARD",
            "2720999999999996, MASTERCARD",
            "378282246310005, AMEX",
            "341111111111111, AMEX",
            "6011111111111117, DISCOVER",
            "6500000000000002, DISCOVER",
            "5000000000000009, UNKNOWN",
            "5600000000000000, UNKNOWN",
            "2220999999999999, UNKNOWN",
            "2721000000000000, UNKNOWN",
            "3530111333300000, UNKNOWN",
            "1234567890123, UNKNOWN"
    })
    void shouldDetectBrandFromPrefix(String digits, CardBrand expected) {
        assertThat(CardBrand.detect(digits)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldReturnUnknownForMissingNumber(String digits) {
        assertThat(CardBrand.detect(digits)).isEqualTo(CardBrand.UNKNOWN);
    }
}
