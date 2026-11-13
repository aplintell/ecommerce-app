package com.example.ecommerce.cart;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * Session-scoped shopping cart. Stores only {@code productId -> quantity}; never prices.
 */
@Component
@SessionScope
public class Cart implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final LinkedHashMap<Long, Integer> items = new LinkedHashMap<>();

    /** Adds the quantity to the line, creating it if absent. */
    public synchronized void add(Long productId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        items.merge(productId, quantity, Integer::sum);
    }

    /** Sets the line quantity; 0 or less removes the line. */
    public synchronized void setQuantity(Long productId, int quantity) {
        if (quantity <= 0) {
            items.remove(productId);
        } else {
            items.put(productId, quantity);
        }
    }

    public synchronized void remove(Long productId) {
        items.remove(productId);
    }

    public synchronized void clear() {
        items.clear();
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    public synchronized int quantityOf(Long productId) {
        return items.getOrDefault(productId, 0);
    }

    /** Unmodifiable snapshot of the lines, in insertion order. */
    public synchronized Map<Long, Integer> lines() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    /** Sum of all quantities. */
    public synchronized int itemCount() {
        int count = 0;
        for (int qty : items.values()) {
            count += qty;
        }
        return count;
    }
}
