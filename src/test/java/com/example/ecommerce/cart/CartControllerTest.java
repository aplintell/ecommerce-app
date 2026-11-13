package com.example.ecommerce.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.example.ecommerce.support.SeedData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Fresh session per test; shared by all requests within one test. */
    private MockHttpSession session;

    private long laptopId;
    private long bookId;
    private long skilletId;
    private long inactiveId;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        laptopId = SeedData.idOf(jdbc, "ELEC-001");
        bookId = SeedData.idOf(jdbc, "BOOK-001");
        skilletId = SeedData.idOf(jdbc, "HOME-LOW");
        inactiveId = SeedData.idOf(jdbc, "CLOTH-OFF");
    }

    private ResultActions add(Object productId, Object quantity) throws Exception {
        return mockMvc.perform(post("/cart/items").session(session).with(csrf())
                .param("productId", String.valueOf(productId))
                .param("quantity", String.valueOf(quantity)));
    }

    private ResultActions update(long productId, String quantity) throws Exception {
        return mockMvc.perform(post("/cart/items/{id}", productId).session(session).with(csrf())
                .param("quantity", quantity));
    }

    private String cartPage() throws Exception {
        return mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String productsPage() throws Exception {
        return mockMvc.perform(get("/products").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void addShouldRedirectToCartWithFlashMessage() throws Exception {
        add(laptopId, 1)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("success", "Added Aurora 4K Laptop to your cart"));

        String html = cartPage();
        assertThat(html).contains("Aurora 4K Laptop", "$1,234.56", "Cart (1)");
        assertThat(html).contains("href=\"/products/" + laptopId + "\"");
        assertThat(html).contains("Proceed to checkout");
    }

    @Test
    void addingSameProductAgainShouldIncreaseQuantity() throws Exception {
        add(laptopId, 1).andExpect(redirectedUrl("/cart"));
        add(laptopId, 2).andExpect(redirectedUrl("/cart"));

        String html = cartPage();
        assertThat(html).contains("Cart (3)");
        assertThat(html).containsPattern("<input[^>]*name=\"quantity\"[^>]*value=\"3\"");
        assertThat(html).contains("$3,703.68");
    }

    @Test
    void cartShouldShowLineTotalsSubtotalAndTotalFromDatabasePrices() throws Exception {
        add(laptopId, 1);
        add(bookId, 2);

        String html = cartPage();
        assertThat(html).contains("Aurora 4K Laptop", "Clean Architecture Handbook");
        assertThat(html).contains("$19.99", "$39.98");
        // Subtotal and total: 1234.56 + 2 * 19.99
        assertThat(countOccurrences(html, "$1,274.54")).isEqualTo(2);
        assertThat(html).contains("Cart (3)");
    }

    @Test
    void headerShouldShowZeroThenUpdatedItemCount() throws Exception {
        assertThat(productsPage()).contains("Cart (0)");

        add(bookId, 2);
        assertThat(productsPage()).contains("Cart (2)");

        add(laptopId, 1);
        assertThat(productsPage()).contains("Cart (3)");
    }

    @Test
    void updateShouldChangeQuantity() throws Exception {
        add(bookId, 1);

        update(bookId, "3")
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attributeExists("success"));

        String html = cartPage();
        assertThat(html).contains("Cart (3)", "$59.97");
    }

    @Test
    void updateToZeroShouldRemoveLine() throws Exception {
        add(bookId, 2);

        update(bookId, "0").andExpect(redirectedUrl("/cart")).andExpect(flash().attributeExists("success"));

        assertThat(cartPage()).contains("Your cart is empty", "Cart (0)");
    }

    @Test
    void updateWithInvalidQuantityShouldRedirectWithErrorAndNotChangeCart() throws Exception {
        add(bookId, 2);

        for (String bad : new String[] {"abc", "-1", "100", ""}) {
            update(bookId, bad)
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/cart"))
                    .andExpect(flash().attributeExists("error"));
        }
        mockMvc.perform(post("/cart/items/{id}", bookId).session(session).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attributeExists("error"));

        assertThat(cartPage()).contains("Cart (2)");
    }

    @Test
    void updateAboveStockShouldShowErrorAndKeepQuantity() throws Exception {
        add(skilletId, 1);

        update(skilletId, "4")
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("error", "Only 3 of Cast Iron Skillet available"));

        assertThat(cartPage()).contains("Cart (1)");
    }

    @Test
    void deleteShouldRemoveLineWithFlash() throws Exception {
        add(bookId, 2);
        add(laptopId, 1);

        mockMvc.perform(post("/cart/items/{id}/delete", bookId).session(session).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attributeExists("success"));

        String html = cartPage();
        assertThat(html).doesNotContain("href=\"/products/" + bookId + "\"").contains("Aurora 4K Laptop", "Cart (1)");
    }

    @Test
    void deleteOfMissingLineShouldBeNoOp() throws Exception {
        mockMvc.perform(post("/cart/items/{id}/delete", bookId).session(session).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"));
        assertThat(cartPage()).contains("Your cart is empty");
    }

    @Test
    void addingMoreThanStockShouldShowErrorAndNotChangeCart() throws Exception {
        add(skilletId, 4)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/products/" + skilletId))
                .andExpect(flash().attribute("error", "Only 3 of Cast Iron Skillet available"));

        assertThat(cartPage()).contains("Your cart is empty", "Cart (0)");
    }

    @Test
    void addThatWouldExceedStockInTotalShouldKeepExistingQuantity() throws Exception {
        add(skilletId, 2).andExpect(redirectedUrl("/cart"));

        add(skilletId, 2)
                .andExpect(redirectedUrl("/products/" + skilletId))
                .andExpect(flash().attribute("error", "Only 3 of Cast Iron Skillet available"));

        assertThat(cartPage()).contains("Cart (2)");
    }

    @Test
    void nonNumericQuantityShouldShowQuantityMustBeANumber() throws Exception {
        add(laptopId, "abc")
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/products/" + laptopId))
                .andExpect(flash().attribute("error", "Quantity must be a number"));

        assertThat(cartPage()).contains("Your cart is empty");
    }

    @Test
    void quantityBelowOneOrAbove99ShouldBeRejectedOnAdd() throws Exception {
        add(laptopId, 0)
                .andExpect(redirectedUrl("/products/" + laptopId))
                .andExpect(flash().attribute("error", "Quantity must be at least 1"));
        add(laptopId, -3)
                .andExpect(redirectedUrl("/products/" + laptopId))
                .andExpect(flash().attributeExists("error"));
        add(bookId, 100)
                .andExpect(redirectedUrl("/products/" + bookId))
                .andExpect(flash().attribute("error", "Quantity cannot be more than 99"));

        assertThat(cartPage()).contains("Your cart is empty");
    }

    @Test
    void nonNumericProductIdShouldRedirectToCartWithError() throws Exception {
        add("abc", 1)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void addingUnknownOrInactiveProductShouldReturn404() throws Exception {
        add(999_999L, 1).andExpect(status().isNotFound());
        add(inactiveId, 1).andExpect(status().isNotFound());

        assertThat(cartPage()).contains("Your cart is empty");
    }

    @Test
    void emptyCartShouldShowMessageWithLinkAndNoCheckoutButton() throws Exception {
        String html = cartPage();

        assertThat(html).contains("Your cart is empty");
        assertThat(html).containsPattern("<a href=\"/products\">Continue shopping</a>");
        assertThat(html).doesNotContain("Proceed to checkout").doesNotContain("href=\"/checkout\"");
        assertThat(html).contains("Cart (0)");
    }

    @Test
    @Sql(scripts = "/sql/restore-stock.sql", executionPhase = BEFORE_TEST_METHOD)
    void lineWithMoreThanCurrentStockShouldShowWarningAndBlockCheckout() throws Exception {
        add(skilletId, 3).andExpect(redirectedUrl("/cart"));
        SeedData.setStock(jdbc, "HOME-LOW", 1);
        try {
            String html = cartPage();
            assertThat(html).contains("Cast Iron Skillet", "Only 1 left in stock");
            assertThat(html).doesNotContain("Proceed to checkout");

            mockMvc.perform(get("/checkout").session(session))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/cart"));

            // Fixing the quantity unblocks checkout.
            update(skilletId, "1").andExpect(flash().attributeExists("success"));
            assertThat(cartPage()).contains("Proceed to checkout");
        } finally {
            SeedData.setStock(jdbc, "HOME-LOW", 3);
        }
    }

    // ---- CSRF ----

    @Test
    void addWithoutCsrfShouldReturn403AndLeaveCartUnchanged() throws Exception {
        mockMvc.perform(post("/cart/items").session(session)
                        .param("productId", String.valueOf(laptopId))
                        .param("quantity", "1"))
                .andExpect(status().isForbidden());

        assertThat(cartPage()).contains("Your cart is empty", "Cart (0)");
    }

    @Test
    void addWithInvalidCsrfTokenShouldReturn403() throws Exception {
        mockMvc.perform(post("/cart/items").session(session).with(csrf().useInvalidToken())
                        .param("productId", String.valueOf(laptopId))
                        .param("quantity", "1"))
                .andExpect(status().isForbidden());

        assertThat(cartPage()).contains("Your cart is empty");
    }

    @Test
    void updateAndDeleteWithoutCsrfShouldReturn403AndLeaveCartUnchanged() throws Exception {
        add(bookId, 2);

        mockMvc.perform(post("/cart/items/{id}", bookId).session(session).param("quantity", "5"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/cart/items/{id}/delete", bookId).session(session))
                .andExpect(status().isForbidden());

        assertThat(cartPage()).contains("Clean Architecture Handbook", "Cart (2)");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
