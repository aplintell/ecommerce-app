package com.example.ecommerce.checkout;

import static com.example.ecommerce.checkout.CheckoutFlowIntegrationTest.APPROVED_PAN;
import static com.example.ecommerce.checkout.CheckoutFlowIntegrationTest.APPROVED_PAN_DIGITS;
import static com.example.ecommerce.checkout.CheckoutFlowIntegrationTest.CVV;
import static com.example.ecommerce.checkout.CheckoutFlowIntegrationTest.CVV_TOKEN;
import static com.example.ecommerce.checkout.CheckoutFlowIntegrationTest.assertCardInputsHaveNoValue;
import static com.example.ecommerce.checkout.CheckoutFlowIntegrationTest.checkoutPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.ecommerce.payment.PaymentGateway;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Sql(scripts = "/sql/restore-stock.sql", executionPhase = BEFORE_TEST_METHOD)
class CheckoutGatewayErrorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private PaymentGateway gateway;

    private MockHttpSession session;
    private long laptopId;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        laptopId = SeedData.idOf(jdbc, "ELEC-001");
        when(gateway.charge(any())).thenThrow(new RuntimeException("boom 4242424242424242"));
    }

    @Test
    void gatewayExceptionShouldRerenderWithErrorAndChangeNothing(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/cart/items").session(session).with(csrf())
                        .param("productId", String.valueOf(laptopId))
                        .param("quantity", "2"))
                .andExpect(redirectedUrl("/cart"));
        long before = SeedData.orderCount(jdbc);
        int logOffset = output.getAll().length();

        String html = mockMvc.perform(checkoutPost(session, APPROVED_PAN, CVV))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/form"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        verify(gateway, times(1)).charge(any());

        // Response
        assertThat(html).contains("Payment could not be processed. Please try again.");
        assertThat(html).doesNotContain(APPROVED_PAN).doesNotContain(APPROVED_PAN_DIGITS).doesNotContain(CVV)
                .doesNotContain("boom");
        assertCardInputsHaveNoValue(html);

        // No order, stock and cart unchanged
        assertThat(SeedData.orderCount(jdbc) - before).isZero();
        assertThat(SeedData.stockOf(jdbc, "ELEC-001")).isEqualTo(50);
        String cart = mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cart).contains("Aurora 4K Laptop", "Cart (2)", "$2,469.12");

        // Logged at ERROR, without card data or the provider's exception message
        String logs = output.getAll().substring(logOffset);
        List<String> errorLines = logs.lines()
                .filter(line -> line.contains("ERROR") && line.contains("Payment processing failed for order"))
                .toList();
        assertThat(errorLines).hasSize(1);
        assertThat(errorLines.get(0)).contains("RuntimeException");
        assertThat(logs).doesNotContain(APPROVED_PAN_DIGITS).doesNotContain(APPROVED_PAN)
                .doesNotContain("boom");
        assertThat(logs).doesNotContainPattern(CVV_TOKEN);
    }
}
