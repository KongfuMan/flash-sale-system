package com.webapps.flashsale.service;

import com.webapps.flashsale.dto.OrderResponse;
import com.webapps.flashsale.dto.OrderItemResponse;
import com.webapps.flashsale.dto.OrderPageResponse;
import com.webapps.flashsale.entity.OrderItemEntity;
import com.webapps.flashsale.repository.OrderItemRepository;
import com.webapps.flashsale.repository.OrderRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final OrderItemRepository items;

    public OrderService(OrderRepository orders, OrderItemRepository items) {
        this.orders = orders;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public OrderPageResponse getOrders(int page, int size) {
        var slice = orders.findAllBy(PageRequest.of(page, size, Sort.by("orderId")));
        var ids = slice.getContent().stream().map(order -> order.orderId).toList();
        Map<Long, List<OrderItemEntity>> itemsByOrder = ids.isEmpty() ? Map.of()
                : items.findByOrderIdInOrderByOrderItemIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(item -> item.orderId));
        var result = slice.getContent().stream()
                .map(order -> new OrderResponse(order.orderId, order.userId, order.eventId,
                        order.eventProductId, order.status, order.expiresAt, order.createdAt, order.updatedAt,
                        itemsByOrder.getOrDefault(order.orderId, List.of()).stream()
                                .map(item -> new OrderItemResponse(item.orderItemId, item.productId,
                                        item.eventProductId, item.quantity, item.unitPriceSnapshot))
                                .toList()))
                .toList();
        return new OrderPageResponse(result, page, size, slice.hasNext());
    }
}
