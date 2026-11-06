package com.webapps.flashsale.service;

import com.webapps.flashsale.dto.OrderResponse;
import com.webapps.flashsale.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orders.findAll().stream()
                .map(order -> new OrderResponse(order.orderId, order.userId, order.eventId,
                        order.eventProductId, order.status, order.expiresAt, order.createdAt, order.updatedAt))
                .toList();
    }
}
