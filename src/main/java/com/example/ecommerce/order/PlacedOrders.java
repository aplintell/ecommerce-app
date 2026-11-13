package com.example.ecommerce.order;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * Order numbers placed in the current session. Only these confirmations may be shown to this session.
 */
@Component
@SessionScope
public class PlacedOrders implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final LinkedHashSet<String> orderNumbers = new LinkedHashSet<>();

    public synchronized void add(String orderNumber) {
        orderNumbers.add(orderNumber);
    }

    public synchronized boolean contains(String orderNumber) {
        return orderNumber != null && orderNumbers.contains(orderNumber);
    }

    public synchronized Set<String> orderNumbers() {
        return Set.copyOf(orderNumbers);
    }
}
