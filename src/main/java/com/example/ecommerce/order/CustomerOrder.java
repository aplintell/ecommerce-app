package com.example.ecommerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.ecommerce.payment.CardBrand;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * A placed order. Stores only the card brand and last 4 digits, never the full card number, CVV or expiry.
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", length = 36, nullable = false)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OrderStatus status;

    @Column(name = "customer_name", length = 100, nullable = false)
    private String customerName;

    @Column(name = "email", length = 254, nullable = false)
    private String email;

    @Column(name = "address_line1", length = 200, nullable = false)
    private String addressLine1;

    @Column(name = "address_line2", length = 200, nullable = true)
    private String addressLine2;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "postal_code", length = 20, nullable = false)
    private String postalCode;

    @Column(name = "country", length = 100, nullable = false)
    private String country;

    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    private BigDecimal total;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_brand", length = 20, nullable = false)
    private CardBrand cardBrand;

    @Column(name = "card_last4", length = 4, nullable = false)
    private String cardLast4;

    @Column(name = "payment_transaction_id", length = 100, nullable = false)
    private String paymentTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String orderNumber, OrderStatus status, String customerName, String email,
                         String addressLine1, String addressLine2, String city, String postalCode, String country,
                         BigDecimal subtotal, BigDecimal total, String currency, CardBrand cardBrand,
                         String cardLast4, String paymentTransactionId, Instant createdAt) {
        this.orderNumber = orderNumber;
        this.status = status;
        this.customerName = customerName;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.postalCode = postalCode;
        this.country = country;
        this.subtotal = subtotal;
        this.total = total;
        this.currency = currency;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.paymentTransactionId = paymentTransactionId;
        this.createdAt = createdAt;
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /** Display form such as {@code VISA •••• 4242}. */
    public String getMaskedCard() {
        return cardBrand + " •••• " + cardLast4;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getEmail() {
        return email;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public CardBrand getCardBrand() {
        return cardBrand;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
