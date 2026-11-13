package com.example.ecommerce.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final PlacedOrders placedOrders;

    public OrderService(OrderRepository orderRepository, PlacedOrders placedOrders) {
        this.orderRepository = orderRepository;
        this.placedOrders = placedOrders;
    }

    /**
     * Returns the order (with items) only if it was placed in the current session.
     *
     * @throws OrderNotFoundException if unknown or placed in another session
     */
    public CustomerOrder getConfirmation(String orderNumber) {
        if (!placedOrders.contains(orderNumber)) {
            throw new OrderNotFoundException();
        }
        return orderRepository.findByOrderNumber(orderNumber).orElseThrow(OrderNotFoundException::new);
    }
}
