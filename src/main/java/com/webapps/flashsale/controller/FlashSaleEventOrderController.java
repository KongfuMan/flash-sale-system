package com.webapps.flashsale.controller;

import com.webapps.flashsale.dto.OrderPageResponse;
import com.webapps.flashsale.dto.OrderResponse;
import com.webapps.flashsale.dto.ReservationRequest;
import com.webapps.flashsale.dto.ReservationResponse;
import com.webapps.flashsale.service.OrderService;
import com.webapps.flashsale.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/event/")
public class FlashSaleEventOrderController {
    private final OrderService orders;
    private final ReservationService reservations;

    public FlashSaleEventOrderController(OrderService orders, ReservationService reservations) {
        this.orders = orders;
        this.reservations = reservations;
    }

    @GetMapping("{eventId}/orders")
    public OrderPageResponse getOrders(@PathVariable long eventId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100");
        }
        return orders.getOrders(page, size);
    }

    @GetMapping("{eventId}/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable long eventId,
                                                  @PathVariable long orderId) {
        var order = orders.getOrder(orderId);
        return ResponseEntity.of(order);
    }

    @PostMapping("/{eventId}/product/{productId}")
    public ResponseEntity<ReservationResponse> placeOrder(
            @PathVariable long eventId,
            @PathVariable long productId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("UserId") long userId) {
        if (eventId <= 0 || productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId and productId must be positive");
        }

        var result = reservations.reserve(eventId, productId, userId, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new ReservationResponse(result.orderId()));
    }
}
