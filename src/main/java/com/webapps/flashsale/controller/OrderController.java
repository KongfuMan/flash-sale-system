package com.webapps.flashsale.controller;

import com.webapps.flashsale.dto.OrderResponse;
import com.webapps.flashsale.service.OrderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orders.getAllOrders();
    }
}
