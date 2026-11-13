package com.example.ecommerce.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.payment.CardBrand;
import com.example.ecommerce.payment.PaymentGateway;
import com.example.ecommerce.payment.PaymentResult;
import com.example.ecommerce.support.SeedData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

/**
 * Two buyers race for the last unit of ELEC-LAST (stock 1). The pessimistic lock (and the {@code @Version}
 * column as a backstop) must let exactly one of them through.
 */
@SpringBootTest
@Sql(scripts = "/sql/restore-stock.sql", executionPhase = BEFORE_TEST_METHOD)
class CheckoutConcurrencyTest {

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private PaymentGateway gateway;

    private long lastId;

    @BeforeEach
    void setUp() {
        lastId = SeedData.idOf(jdbc, "ELEC-LAST");
        // Stubbed before the workers start; the short delay widens the window in which both are in flight.
        when(gateway.charge(any())).thenAnswer(invocation -> {
            Thread.sleep(200);
            return PaymentResult.approved("fake_concurrent", "4242", CardBrand.VISA);
        });
    }

    @RepeatedTest(3)
    void onlyOneBuyerShouldGetTheLastUnit() throws Exception {
        assertThat(SeedData.stockOf(jdbc, "ELEC-LAST")).isEqualTo(1);
        long before = SeedData.orderCount(jdbc);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CustomerOrder>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return checkoutService.placeOrder(CheckoutServiceTest.validForm("4242424242424242"),
                            Map.of(lastId, 1));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            List<Throwable> failures = new ArrayList<>();
            for (Future<CustomerOrder> future : futures) {
                try {
                    CustomerOrder order = future.get(30, TimeUnit.SECONDS);
                    assertThat(order.getOrderNumber()).isNotBlank();
                    successes++;
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }

            assertThat(successes).as("successful checkouts").isEqualTo(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).as("losing checkout failure")
                    .isInstanceOfAny(InsufficientStockException.class,
                            ObjectOptimisticLockingFailureException.class);
        } finally {
            executor.shutdownNow();
        }

        assertThat(SeedData.orderCount(jdbc) - before).isEqualTo(1);
        assertThat(SeedData.stockOf(jdbc, "ELEC-LAST")).isZero();
        verify(gateway, times(1)).charge(any());
    }
}
