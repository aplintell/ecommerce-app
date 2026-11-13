package com.example.ecommerce.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import java.math.BigDecimal;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.order.OrderItem;
import com.example.ecommerce.order.OrderRepository;
import com.example.ecommerce.order.OrderStatus;
import com.example.ecommerce.payment.CardBrand;
import com.example.ecommerce.payment.PaymentGateway;
import com.example.ecommerce.payment.PaymentRequest;
import com.example.ecommerce.payment.PaymentResult;
import com.example.ecommerce.support.SeedData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

/**
 * Not {@code @Transactional}: the service must commit or roll back on its own.
 */
@SpringBootTest
@Sql(scripts = "/sql/restore-stock.sql", executionPhase = BEFORE_TEST_METHOD)
class CheckoutServiceTest {

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private PaymentGateway gateway;

    private long laptopId;
    private long bookId;
    private long lastId;
    private long inactiveId;

    @BeforeEach
    void setUp() {
        laptopId = SeedData.idOf(jdbc, "ELEC-001");
        bookId = SeedData.idOf(jdbc, "BOOK-001");
        lastId = SeedData.idOf(jdbc, "ELEC-LAST");
        inactiveId = SeedData.idOf(jdbc, "CLOTH-OFF");
    }

    static CheckoutForm validForm(String cardNumber) {
        CheckoutForm form = new CheckoutForm();
        form.setFullName("Jane Doe");
        form.setEmail("jane@example.com");
        form.setAddressLine1("1 Main Street");
        form.setAddressLine2("");
        form.setCity("Springfield");
        form.setPostalCode("12345");
        form.setCountry("United States");
        form.setCardholderName("Jane Doe");
        form.setCardNumber(cardNumber);
        form.setExpiryMonth(12);
        form.setExpiryYear(Year.now().getValue() + 2);
        form.setCvv("7373");
        return form;
    }

    private Map<Long, Integer> lines(Object... idQty) {
        Map<Long, Integer> lines = new LinkedHashMap<>();
        for (int i = 0; i < idQty.length; i += 2) {
            lines.put((Long) idQty[i], (Integer) idQty[i + 1]);
        }
        return lines;
    }

    @Test
    void successShouldDecrementStockSaveOrderAndChargeServerTotal() {
        when(gateway.charge(any())).thenReturn(PaymentResult.approved("fake_tx_123", "4242", CardBrand.VISA));
        long before = SeedData.orderCount(jdbc);

        CustomerOrder placed = checkoutService.placeOrder(validForm("4242 4242 4242 4242"),
                lines(laptopId, 1, bookId, 2));

        assertThat(SeedData.orderCount(jdbc) - before).isEqualTo(1);
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(49);
        assertThat(SeedData.stockOf(jdbc, "BOOK-001")).isEqualTo(98);

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(gateway, times(1)).charge(captor.capture());
        PaymentRequest request = captor.getValue();
        // 1234.56 * 1 + 19.99 * 2 from database prices
        assertThat(request.amount()).isEqualTo(new BigDecimal("1274.54"));
        assertThat(request.currency()).isEqualTo("USD");
        assertThat(request.card().number()).isEqualTo("4242424242424242");
        assertThat(request.orderReference()).isEqualTo(placed.getOrderNumber());

        CustomerOrder order = orderRepository.findByOrderNumber(placed.getOrderNumber()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getCardLast4()).isEqualTo("4242");
        assertThat(order.getCardBrand()).isEqualTo(CardBrand.VISA);
        assertThat(order.getPaymentTransactionId()).isEqualTo("fake_tx_123");
        assertThat(order.getCurrency()).isEqualTo("USD");
        assertThat(order.getSubtotal()).isEqualByComparingTo("1274.54");
        assertThat(order.getTotal()).isEqualByComparingTo("1274.54");
        assertThat(order.getCustomerName()).isEqualTo("Jane Doe");
        assertThat(order.getAddressLine2()).isNull();
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getOrderNumber()).hasSize(36);

        assertThat(order.getItems()).hasSize(2);
        OrderItem laptopItem = order.getItems().stream()
                .filter(i -> i.getProductId().equals(laptopId)).findFirst().orElseThrow();
        assertThat(laptopItem.getProductName()).isEqualTo("Aurora 4K Laptop");
        assertThat(laptopItem.getUnitPrice()).isEqualByComparingTo("1234.56");
        assertThat(laptopItem.getQuantity()).isEqualTo(1);
        assertThat(laptopItem.getLineTotal()).isEqualByComparingTo("1234.56");
        OrderItem bookItem = order.getItems().stream()
                .filter(i -> i.getProductId().equals(bookId)).findFirst().orElseThrow();
        assertThat(bookItem.getProductName()).isEqualTo("Clean Architecture Handbook");
        assertThat(bookItem.getUnitPrice()).isEqualByComparingTo("19.99");
        assertThat(bookItem.getQuantity()).isEqualTo(2);
        assertThat(bookItem.getLineTotal()).isEqualByComparingTo("39.98");
    }

    @Test
    void gatewayAmountShouldEqualDatabasePriceTimesQuantity() {
        when(gateway.charge(any())).thenReturn(PaymentResult.approved("fake_tx_9", "4242", CardBrand.VISA));

        checkoutService.placeOrder(validForm("4242424242424242"), lines(laptopId, 3));

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(gateway).charge(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(new BigDecimal("3703.68"));
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(47);
    }

    @Test
    void declineShouldSaveNoOrderAndKeepStock() {
        when(gateway.charge(any())).thenReturn(PaymentResult.declined("card_declined", "0002", CardBrand.VISA));
        long before = SeedData.orderCount(jdbc);

        assertThatThrownBy(() -> checkoutService.placeOrder(validForm("4000 0000 0000 0002"),
                lines(laptopId, 1, bookId, 2)))
                .isInstanceOf(PaymentDeclinedException.class)
                .satisfies(e -> assertThat(((PaymentDeclinedException) e).getDeclineCode())
                        .isEqualTo("card_declined"));

        assertThat(SeedData.orderCount(jdbc) - before).isZero();
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(50);
        assertThat(SeedData.stockOf(jdbc, "BOOK-001")).isEqualTo(100);
    }

    @Test
    void gatewayRuntimeExceptionShouldBecomePaymentProcessingExceptionWithoutCardData() {
        when(gateway.charge(any())).thenThrow(new RuntimeException("boom 4242424242424242"));
        long before = SeedData.orderCount(jdbc);

        assertThatThrownBy(() -> checkoutService.placeOrder(validForm("4242424242424242"), lines(laptopId, 1)))
                .isInstanceOf(PaymentProcessingException.class)
                .satisfies(e -> {
                    assertThat(e.getCause()).isNull();
                    assertThat(e.getMessage()).doesNotContain("4242424242424242").doesNotContain("boom");
                    assertThat(e.getSuppressed()).isEmpty();
                });

        assertThat(SeedData.orderCount(jdbc) - before).isZero();
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(50);
    }

    @Test
    void insufficientStockShouldNotChargeOrSaveOrder() {
        long before = SeedData.orderCount(jdbc);

        assertThatThrownBy(() -> checkoutService.placeOrder(validForm("4242424242424242"), lines(lastId, 2)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Studio Monitor Headphones only has 1 left in stock");

        verify(gateway, never()).charge(any());
        assertThat(SeedData.orderCount(jdbc) - before).isZero();
        assertThat(SeedData.stockOf(jdbc, "ELEC-LAST")).isEqualTo(1);
    }

    @Test
    void insufficientStockOnOneLineShouldLeaveOtherLinesUntouched() {
        assertThatThrownBy(() -> checkoutService.placeOrder(validForm("4242424242424242"),
                lines(laptopId, 1, lastId, 2)))
                .isInstanceOf(InsufficientStockException.class);

        verify(gateway, never()).charge(any());
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(50);
    }

    @Test
    void inactiveOrMissingProductShouldBeInsufficientStockWithoutCharge() {
        assertThatThrownBy(() -> checkoutService.placeOrder(validForm("4242424242424242"), lines(inactiveId, 1)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Discontinued Denim Jacket");
        assertThatThrownBy(() -> checkoutService.placeOrder(validForm("4242424242424242"), lines(999_999L, 1)))
                .isInstanceOf(InsufficientStockException.class);

        verify(gateway, never()).charge(any());
    }

    @Test
    void emptyLinesShouldThrowEmptyCartException() {
        assertThatThrownBy(() -> checkoutService.placeOrder(validForm("4242424242424242"), Map.of()))
                .isInstanceOf(EmptyCartException.class);
        verify(gateway, never()).charge(any());
    }
}
