package com.example.ecommerce.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class MoneyFormatterTest {

    private final MoneyFormatter formatter = new MoneyFormatter();

    @Test
    void shouldFormatThousandsWithUsSeparatorsAndDollarSign() {
        assertThat(formatter.format(new BigDecimal("1234.56"))).isEqualTo("$1,234.56");
    }

    @Test
    void shouldFormatZeroWithTwoDecimals() {
        assertThat(formatter.format(BigDecimal.ZERO)).isEqualTo("$0.00");
    }

    @Test
    void shouldPadSingleDecimalToTwoDigits() {
        assertThat(formatter.format(new BigDecimal("19.9"))).isEqualTo("$19.90");
    }

    @Test
    void shouldIgnoreJvmDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(formatter.format(new BigDecimal("1234.56"))).isEqualTo("$1,234.56");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
