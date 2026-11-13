package com.example.ecommerce.checkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.ecommerce.catalog.Product;
import com.example.ecommerce.catalog.ProductRepository;
import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.order.OrderItem;
import com.example.ecommerce.order.OrderRepository;
import com.example.ecommerce.order.OrderStatus;
import com.example.ecommerce.payment.CardDetails;
import com.example.ecommerce.payment.CardNumbers;
import com.example.ecommerce.payment.PaymentGateway;
import com.example.ecommerce.payment.PaymentRequest;
import com.example.ecommerce.payment.PaymentResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway gateway;
    private final Clock clock;
    private final String currency;

    public CheckoutService(ProductRepository productRepository, OrderRepository orderRepository,
                           PaymentGateway gateway, Clock clock,
                           @Value("${app.currency:USD}") String currency) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.gateway = gateway;
        this.clock = clock;
        this.currency = currency;
    }

    /**
     * Places an order for the given cart lines and charges the card.
     * <p>
     * Runs in a single transaction: products are locked (pessimistic write, ordered by id), stock is
     * re-checked, the gateway is charged the server-computed total, stock is decremented and the order is
     * saved. Any exception (insufficient stock, decline, gateway failure) rolls everything back.
     * <p>
     * TODO (A6): the gateway is called while row locks are held. That is fine for the in-process fake
     * gateway, but a real provider should use a reserve -> charge -> confirm/compensate flow so that locks
     * are not held during a network call.
     *
     * @param lines productId -> quantity, from the session cart (no prices)
     */
    @Transactional
    public CustomerOrder placeOrder(CheckoutForm form, Map<Long, Integer> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new EmptyCartException();
        }

        Map<Long, Product> products = productRepository.findAllByIdForUpdate(lines.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> line : lines.entrySet()) {
            Product product = products.get(line.getKey());
            if (product == null) {
                throw new InsufficientStockException("A product in your cart is no longer available");
            }
            if (!product.isActive()) {
                throw new InsufficientStockException(product.getName() + " is no longer available");
            }
            int quantity = line.getValue();
            if (product.getStockQuantity() < quantity) {
                throw new InsufficientStockException(
                        product.getName() + " only has " + product.getStockQuantity() + " left in stock");
            }
            BigDecimal unitPrice = product.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            items.add(new OrderItem(product.getId(), product.getName(), unitPrice, quantity, lineTotal));
        }
        BigDecimal total = subtotal.setScale(2, RoundingMode.HALF_UP);

        String orderNumber = UUID.randomUUID().toString();
        CardDetails card = new CardDetails(
                CardNumbers.digitsOnly(form.getCardNumber()),
                form.getExpiryMonth(),
                form.getExpiryYear(),
                form.getCvv(),
                form.getCardholderName());
        PaymentRequest request = new PaymentRequest(orderNumber, total, currency, card);

        PaymentResult result;
        try {
            result = gateway.charge(request);
        } catch (RuntimeException e) {
            // Never log the exception message or stack trace: a provider could include card data in it.
            log.error("Payment processing failed for order {}: {}", orderNumber, e.getClass().getSimpleName());
            throw new PaymentProcessingException();
        }
        if (!result.approved()) {
            log.info("Payment declined for order {} card ****{} code {}",
                    orderNumber, result.cardLast4(), result.declineCode());
            throw new PaymentDeclinedException(result.declineCode());
        }

        for (Map.Entry<Long, Integer> line : lines.entrySet()) {
            products.get(line.getKey()).decrementStock(line.getValue());
        }

        CustomerOrder order = new CustomerOrder(
                orderNumber,
                OrderStatus.PAID,
                form.getFullName().trim(),
                form.getEmail().trim(),
                form.getAddressLine1().trim(),
                blankToNull(form.getAddressLine2()),
                form.getCity().trim(),
                form.getPostalCode().trim(),
                form.getCountry().trim(),
                subtotal,
                total,
                currency,
                result.brand(),
                result.cardLast4(),
                result.transactionId(),
                Instant.now(clock));
        items.forEach(order::addItem);
        return orderRepository.save(order);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
