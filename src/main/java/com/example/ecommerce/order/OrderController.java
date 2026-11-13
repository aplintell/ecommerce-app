package com.example.ecommerce.order;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders/{orderNumber}/confirmation")
    public String confirmation(@PathVariable String orderNumber, Model model) {
        model.addAttribute("order", orderService.getConfirmation(orderNumber));
        return "orders/confirmation";
    }
}
