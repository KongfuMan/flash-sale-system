package com.webapps.flashsale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webapps.flashsale.contract.MessageEnvelope;
import com.webapps.flashsale.contract.MessageType;
import com.webapps.flashsale.contract.OrderRequested;
import com.webapps.flashsale.dto.OrderResponse;
import com.webapps.flashsale.dto.OrderItemResponse;
import com.webapps.flashsale.dto.OrderPageResponse;
import com.webapps.flashsale.entity.OrderItemEntity;
import com.webapps.flashsale.entity.OrderEntity;
import com.webapps.flashsale.inventory.InventoryPrimitives;
import com.webapps.flashsale.inventory.InventoryReservationResult;
import com.webapps.flashsale.repository.OrderItemRepository;
import com.webapps.flashsale.repository.OrderRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final InventoryPrimitives inventory;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public OrderService(OrderRepository orders, OrderItemRepository items, InventoryPrimitives inventory,
                        KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                        @Value("${reservation.kafka.topic:order-requests-v1}") String topic) {
        this.orders = orders;
        this.items = items;
        this.inventory = inventory;
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    public InventoryReservationResult reserve(long flashSaleEventId, long eventProductId, long userId, String idempotencyKey) {
        InventoryReservationResult result;
        try {
            result = inventory.reserve(flashSaleEventId, eventProductId, userId, idempotencyKey);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory unavailable", ex);
        }

        if (result == InventoryReservationResult.RESERVED) {
            var messageId = UUID.nameUUIDFromBytes((flashSaleEventId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
            var message = new MessageEnvelope<>(messageId, MessageType.ORDER_REQUESTED, 2,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    new OrderRequested(flashSaleEventId, userId, List.of(new OrderRequested.Item(eventProductId, 1))));
            try {
                kafka.send(topic, messageId.toString(), mapper.writeValueAsString(message)).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order publication uncertain", ex);
            } catch (JsonProcessingException | ExecutionException | TimeoutException | RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order publication uncertain", ex);
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public OrderPageResponse getOrders(int page, int size) {
        var slice = orders.findAllBy(PageRequest.of(page, size, Sort.by("orderId")));
        var ids = slice.getContent().stream().map(order -> order.orderId).toList();
        Map<Long, List<OrderItemEntity>> itemsByOrder = ids.isEmpty() ? Map.of()
                : items.findByOrderIdInOrderByOrderItemIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(item -> item.orderId));
        var result = slice.getContent().stream()
                .map(order -> toResponse(order, itemsByOrder.getOrDefault(order.orderId, List.of())))
                .toList();
        return new OrderPageResponse(result, page, size, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> getOrder(long id) {
        return orders.findById(id).map(order -> toResponse(order,
                items.findByOrderIdInOrderByOrderItemIdAsc(List.of(id))));
    }

    private OrderResponse toResponse(OrderEntity order, List<OrderItemEntity> orderItems) {
        return new OrderResponse(order.orderId, order.userId, order.eventId,
                order.eventProductId, order.status, order.expiresAt, order.createdAt, order.updatedAt,
                orderItems.stream().map(item -> new OrderItemResponse(item.orderItemId, item.productId,
                        item.eventProductId, item.quantity, item.unitPriceSnapshot)).toList());
    }
}
