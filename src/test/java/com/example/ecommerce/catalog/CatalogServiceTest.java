package com.example.ecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

class CatalogServiceTest {

    @ParameterizedTest
    @CsvSource({"4,4", "8,8", "12,12", "16,16", "20,20", "24,24", "' 8 ',8"})
    void parsePageSizeShouldAcceptStepsOfFourFromFourToTwentyFour(String input, int expected) {
        assertThat(CatalogService.parsePageSize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @CsvSource({"''", "abc", "0", "-4", "3", "7", "28", "100", "2147483648"})
    void parsePageSizeShouldFallBackToDefaultForInvalidInput(String input) {
        assertThat(CatalogService.parsePageSize(input)).isEqualTo(CatalogService.PAGE_SIZE);
    }
}
