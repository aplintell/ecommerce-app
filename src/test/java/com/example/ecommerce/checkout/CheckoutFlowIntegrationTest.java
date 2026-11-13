package com.example.ecommerce.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.ecommerce.support.SeedData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end checkout through MockMvc with the real {@code FakePaymentGateway}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Sql(scripts = "/sql/restore-stock.sql", executionPhase = BEFORE_TEST_METHOD)
class CheckoutFlowIntegrationTest {

    static final String APPROVED_PAN = "4242 4242 4242 4242";
    static final String APPROVED_PAN_DIGITS = "4242424242424242";
    static final String DECLINE_PAN = "4000 0000 0000 0002";
    static final String DECLINE_PAN_DIGITS = "4000000000000002";
    static final String LUHN_INVALID_PAN = "4242424242424241";
    static final String CVV = "7373";
    /** The CVV as a standalone token (not part of a longer alphanumeric run such as a UUID). */
    static final String CVV_TOKEN = "(?<![0-9A-Za-z])" + CVV + "(?![0-9A-Za-z])";

    static final String DECLINED_MESSAGE = "Your card was declined. Please use a different card.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private MockHttpSession session;
    private long laptopId;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        laptopId = SeedData.idOf(jdbc, "ELEC-001");
    }

    // ---- helpers ----

    private void addToCart(long productId, int quantity) throws Exception {
        mockMvc.perform(post("/cart/items").session(session).with(csrf())
                        .param("productId", String.valueOf(productId))
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"));
    }

    static MockHttpServletRequestBuilder checkoutPost(MockHttpSession session, String pan, String cvv) {
        return checkoutPost(session, pan, cvv, Map.of());
    }

    /**
     * Valid checkout POST. {@code overrides} REPLACE default field values (MockMvc's {@code param()} would
     * append a second value instead).
     */
    static MockHttpServletRequestBuilder checkoutPost(MockHttpSession session, String pan, String cvv,
                                                      Map<String, String> overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("fullName", "Jane Doe");
        fields.put("email", "jane@example.com");
        fields.put("addressLine1", "1 Main Street");
        fields.put("addressLine2", "Apt 2");
        fields.put("city", "Springfield");
        fields.put("postalCode", "12345");
        fields.put("country", "United States");
        fields.put("cardholderName", "Jane Doe");
        fields.put("cardNumber", pan);
        fields.put("expiryMonth", "12");
        fields.put("expiryYear", String.valueOf(Year.now().getValue() + 2));
        fields.put("cvv", cvv);
        fields.putAll(overrides);

        MockHttpServletRequestBuilder builder = post("/checkout").session(session).with(csrf());
        fields.forEach(builder::param);
        return builder;
    }

    /** Output captured from now on (the extension accumulates output for the whole class). */
    private static String outputSince(CapturedOutput output, int offset) {
        return output.getAll().substring(offset);
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String cartPage() throws Exception {
        return body(mockMvc.perform(get("/cart").session(session)).andExpect(status().isOk()).andReturn());
    }

    /** Neither card input may carry a value attribute. */
    static void assertCardInputsHaveNoValue(String html) {
        Matcher card = Pattern.compile("<input[^>]*name=\"cardNumber\"[^>]*>").matcher(html);
        assertThat(card.find()).as("cardNumber input rendered").isTrue();
        assertThat(card.group()).doesNotContain("value=");
        Matcher cvv = Pattern.compile("<input[^>]*name=\"cvv\"[^>]*>").matcher(html);
        assertThat(cvv.find()).as("cvv input rendered").isTrue();
        assertThat(cvv.group()).doesNotContain("value=");
    }

    // ---- happy path ----

    @Test
    void happyPathShouldPlaceOrderShowConfirmationAndClearCart() throws Exception {
        long before = SeedData.orderCount(jdbc);
        addToCart(laptopId, 1);

        String form = body(mockMvc.perform(get("/checkout").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/form"))
                .andReturn());
        assertThat(form).contains("Order summary", "Aurora 4K Laptop", "$1,234.56");
        for (String field : List.of("fullName", "email", "addressLine1", "addressLine2", "city", "postalCode",
                "country", "cardholderName", "cardNumber", "expiryMonth", "expiryYear", "cvv")) {
            assertThat(form).as("field %s", field).contains("name=\"" + field + "\"");
        }
        assertCardInputsHaveNoValue(form);

        MvcResult submit = mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("/orders/*/confirmation"))
                .andReturn();
        String confirmationUrl = submit.getResponse().getRedirectedUrl();
        String orderNumber = confirmationUrl.substring("/orders/".length(), confirmationUrl.indexOf("/confirmation"));
        assertThat(UUID.fromString(orderNumber)).isNotNull();

        assertThat(SeedData.orderCount(jdbc) - before).isEqualTo(1);
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(49);
        Map<String, Object> row = jdbc.queryForMap(
                "select status, card_brand, card_last4, payment_transaction_id, total from customer_order "
                        + "where order_number = ?", orderNumber);
        assertThat(row.get("status")).isEqualTo("PAID");
        assertThat(row.get("card_brand")).isEqualTo("VISA");
        assertThat(row.get("card_last4")).isEqualTo("4242");
        assertThat((String) row.get("payment_transaction_id")).startsWith("fake_");
        assertThat((BigDecimal) row.get("total")).isEqualByComparingTo("1234.56");

        String confirmation = body(mockMvc.perform(get(confirmationUrl).session(session))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(confirmation).contains("VISA •••• 4242", orderNumber, "$1,234.56", "Aurora 4K Laptop");
        assertThat(confirmation).contains("Jane Doe", "1 Main Street", "Apt 2", "Springfield", "12345",
                "United States");
        assertThat(confirmation).doesNotContain(APPROVED_PAN_DIGITS).doesNotContain(APPROVED_PAN);

        assertThat(cartPage()).contains("Your cart is empty", "Cart (0)");

        // Another session may not see the confirmation.
        mockMvc.perform(get(confirmationUrl).session(new MockHttpSession()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
        // Unknown order number, even in the same session.
        mockMvc.perform(get("/orders/{n}/confirmation", UUID.randomUUID()).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void resubmitAfterSuccessShouldRedirectToCartWithoutSecondOrder() throws Exception {
        addToCart(laptopId, 1);
        long before = SeedData.orderCount(jdbc);
        mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV)).andExpect(status().isFound());

        mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"));

        assertThat(SeedData.orderCount(jdbc) - before).isEqualTo(1);
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(49);
    }

    @Test
    void cardNumberWithDashesShouldBeAccepted() throws Exception {
        addToCart(laptopId, 1);

        mockMvc.perform(checkoutPost(session, "4242-4242-4242-4242", CVV))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("/orders/*/confirmation"));
    }

    // ---- empty cart ----

    @Test
    void getCheckoutWithEmptyCartShouldRedirectToCart() throws Exception {
        mockMvc.perform(get("/checkout").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"));
    }

    @Test
    void postCheckoutWithEmptyCartAndInvalidFormShouldRedirectBeforeValidation() throws Exception {
        long before = SeedData.orderCount(jdbc);

        mockMvc.perform(post("/checkout").session(session).with(csrf()).param("email", "not-an-email"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"));

        assertThat(SeedData.orderCount(jdbc) - before).isZero();
    }

    // ---- validation / card echo ----

    @Test
    void luhnInvalidCardShouldRerenderWithoutEchoingCardNumberOrCvv() throws Exception {
        addToCart(laptopId, 1);
        long before = SeedData.orderCount(jdbc);

        String html = body(mockMvc.perform(checkoutPost(session, LUHN_INVALID_PAN, CVV))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/form"))
                .andExpect(model().attributeHasFieldErrors("checkoutForm", "cardNumber"))
                .andReturn());

        assertThat(html).contains("Card number is invalid");
        assertThat(html).doesNotContain(LUHN_INVALID_PAN).doesNotContain(CVV);
        assertCardInputsHaveNoValue(html);
        // Non-card fields keep their values.
        assertThat(html).containsPattern("<input[^>]*name=\"email\"[^>]*value=\"jane@example.com\"");
        assertThat(html).containsPattern("<input[^>]*name=\"fullName\"[^>]*value=\"Jane Doe\"");
        // Server-computed summary is still shown.
        assertThat(html).contains("Order summary", "$1,234.56");
        assertThat(SeedData.orderCount(jdbc) - before).isZero();
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(50);
    }

    @Test
    void otherInvalidFieldShouldNotEchoValidCardNumberOrCvv() throws Exception {
        addToCart(laptopId, 1);
        long before = SeedData.orderCount(jdbc);

        String html = body(mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV, Map.of("fullName", "")))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/form"))
                .andExpect(model().attributeHasFieldErrors("checkoutForm", "fullName"))
                .andReturn());

        assertThat(html).contains("Full name is required");
        assertThat(html).doesNotContain(APPROVED_PAN).doesNotContain(APPROVED_PAN_DIGITS).doesNotContain(CVV);
        assertCardInputsHaveNoValue(html);
        assertThat(html).containsPattern("<input[^>]*name=\"city\"[^>]*value=\"Springfield\"");
        assertThat(SeedData.orderCount(jdbc) - before).isZero();
    }

    @Test
    void invalidEmailExpiredCardAndBadCvvShouldShowFieldErrors() throws Exception {
        addToCart(laptopId, 1);
        long before = SeedData.orderCount(jdbc);

        String html = body(mockMvc.perform(checkoutPost(session, APPROVED_PAN, "12", Map.of(
                        "email", "not-an-email",
                        "expiryMonth", "1",
                        "expiryYear", String.valueOf(Year.now().getValue() - 1),
                        "country", "x".repeat(101))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("checkoutForm", "email", "expiryMonth", "cvv",
                        "country"))
                .andReturn());

        assertThat(html).contains("Email is invalid", "Card has expired", "CVV must be 3 or 4 digits",
                "Country must be at most 100 characters");
        assertCardInputsHaveNoValue(html);
        assertThat(SeedData.orderCount(jdbc) - before).isZero();
    }

    @Test
    void nonNumericExpiryShouldShowTypeMismatchMessage() throws Exception {
        addToCart(laptopId, 1);

        String html = body(mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV, Map.of("expiryMonth", "ab")))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(html).contains("Expiry month must be a number");
        assertThat(html).doesNotContain(APPROVED_PAN).doesNotContain(CVV);
    }

    // ---- decline ----

    @Test
    void declinedCardShouldRerenderWithMessageAndKeepStockAndCart() throws Exception {
        addToCart(laptopId, 1);
        long before = SeedData.orderCount(jdbc);

        String html = body(mockMvc.perform(checkoutPost(session, DECLINE_PAN, CVV))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/form"))
                .andReturn());

        assertThat(html).contains(DECLINED_MESSAGE);
        assertThat(html).doesNotContain(DECLINE_PAN).doesNotContain(DECLINE_PAN_DIGITS).doesNotContain(CVV);
        assertCardInputsHaveNoValue(html);
        assertThat(SeedData.orderCount(jdbc) - before).isZero();
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(50);
        assertThat(cartPage()).contains("Aurora 4K Laptop", "Cart (1)");

        // The shopper can retry with another card.
        mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("/orders/*/confirmation"));
    }

    @Test
    void insufficientFundsCardShouldShowSameDeclineMessage() throws Exception {
        addToCart(laptopId, 1);

        String html = body(mockMvc.perform(checkoutPost(session, "4000 0000 0000 9995", CVV))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(html).contains(DECLINED_MESSAGE);
    }

    // ---- insufficient stock at checkout ----

    @Test
    void stockDroppedBelowCartQuantityShouldRedirectToCartWithoutCharging(CapturedOutput output) throws Exception {
        int logOffset = output.getAll().length();
        long lastId = SeedData.idOf(jdbc, "ELEC-LAST");
        addToCart(lastId, 1);
        SeedData.setStock(jdbc, "ELEC-LAST", 0);
        long before = SeedData.orderCount(jdbc);

        mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("error", "Studio Monitor Headphones only has 0 left in stock"));

        assertThat(SeedData.orderCount(jdbc) - before).isZero();
        assertThat(SeedData.stockOf(jdbc, "ELEC-LAST")).isZero();
        assertThat(outputSince(output, logOffset)).doesNotContain("Fake charge");
    }

    // ---- tampering ----

    @Test
    void extraPriceAndTotalParamsShouldBeIgnored() throws Exception {
        addToCart(laptopId, 2);
        long before = SeedData.orderCount(jdbc);

        MvcResult result = mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV)
                        .param("total", "0.01")
                        .param("price", "0.01")
                        .param("subtotal", "0.01"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("/orders/*/confirmation"))
                .andReturn();
        String url = result.getResponse().getRedirectedUrl();
        String orderNumber = url.substring("/orders/".length(), url.indexOf("/confirmation"));

        assertThat(SeedData.orderCount(jdbc) - before).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "select subtotal, total from customer_order where order_number = ?", orderNumber);
        assertThat((BigDecimal) row.get("total")).isEqualByComparingTo("2469.12");
        assertThat((BigDecimal) row.get("subtotal")).isEqualByComparingTo("2469.12");
        BigDecimal itemPrice = jdbc.queryForObject(
                "select i.unit_price from order_item i join customer_order o on o.id = i.order_id "
                        + "where o.order_number = ?", BigDecimal.class, orderNumber);
        assertThat(itemPrice).isEqualByComparingTo("1234.56");
    }

    // ---- no PAN / CVV anywhere ----

    @Test
    void cardDataShouldNotAppearInLogsDatabaseSessionOrHtml(CapturedOutput output) throws Exception {
        int logOffset = output.getAll().length();
        List<String> secrets = List.of(APPROVED_PAN, APPROVED_PAN_DIGITS, "4242-4242-4242-4242",
                DECLINE_PAN, DECLINE_PAN_DIGITS, "4000-0000-0000-0002", LUHN_INVALID_PAN);

        addToCart(laptopId, 1);
        // invalid
        String invalid = body(mockMvc.perform(checkoutPost(session, LUHN_INVALID_PAN, CVV))
                .andExpect(status().isOk()).andReturn());
        // declined
        String declined = body(mockMvc.perform(checkoutPost(session, "4000-0000-0000-0002", CVV))
                .andExpect(status().isOk()).andReturn());
        // successful
        MvcResult success = mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV))
                .andExpect(status().isFound()).andReturn();
        String confirmation = body(mockMvc.perform(get(success.getResponse().getRedirectedUrl()).session(session))
                .andExpect(status().isOk()).andReturn());

        // HTML
        for (String html : List.of(invalid, declined, confirmation)) {
            for (String secret : secrets) {
                assertThat(html).doesNotContain(secret);
            }
        }
        assertThat(invalid).doesNotContain(CVV);
        assertThat(declined).doesNotContain(CVV);

        // Logs
        String logs = outputSince(output, logOffset);
        assertThat(logs).contains("Fake charge");
        for (String secret : secrets) {
            assertThat(logs).doesNotContain(secret);
        }
        // Boundary-aware for the same reason as the session check (log lines contain UUID order numbers).
        assertThat(logs).doesNotContainPattern(CVV_TOKEN);

        // Database
        for (String table : List.of("customer_order", "order_item")) {
            List<Map<String, Object>> rows = jdbc.queryForList("select * from " + table);
            assertThat(rows).isNotEmpty();
            for (Map<String, Object> rowValues : rows) {
                for (Map.Entry<String, Object> column : rowValues.entrySet()) {
                    String value = String.valueOf(column.getValue());
                    for (String secret : secrets) {
                        assertThat(value).as("%s.%s", table, column.getKey()).doesNotContain(secret);
                    }
                }
            }
        }

        // Session: Java-serialize every attribute and scan the bytes.
        List<String> names = Collections.list(session.getAttributeNames());
        assertThat(names).isNotEmpty();
        for (String name : names) {
            Object value = session.getAttribute(name);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(value);
            }
            String serialized = bytes.toString(StandardCharsets.ISO_8859_1);
            String asText = String.valueOf(value);
            for (String secret : secrets) {
                assertThat(serialized).as("serialized session attribute %s", name).doesNotContain(secret);
                assertThat(asText).as("session attribute %s", name).doesNotContain(secret);
            }
            // Boundary-aware: the session legitimately holds UUIDs (CSRF token, order numbers) whose hex
            // digits could contain "7373" by chance; a stored CVV would stand on its own.
            assertThat(serialized).as("serialized session attribute %s", name).doesNotContainPattern(CVV_TOKEN);
            assertThat(asText).as("session attribute %s", name).doesNotContainPattern(CVV_TOKEN);
        }
    }
}
