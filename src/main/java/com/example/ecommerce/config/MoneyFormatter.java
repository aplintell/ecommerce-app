package com.example.ecommerce.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Single place where money is turned into text. Always USD, always en-US formatting,
 * independent of the request locale. Example: 1234.56 renders as {@code $1,234.56}.
 */
@Component("moneyFormatter")
public class MoneyFormatter {

    public String format(BigDecimal value) {
        BigDecimal amount = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        // NumberFormat is not thread-safe, so create one per call.
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        return format.format(amount);
    }
}
