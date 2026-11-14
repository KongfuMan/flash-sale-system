package com.webapps.flashsale.controller;

import com.webapps.flashsale.dto.OrderPageResponse;
import com.webapps.flashsale.dto.OrderResponse;
import com.webapps.flashsale.dto.ErrorResponse;
import com.webapps.flashsale.service.OrderService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/flash_sale_event/")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * Returns a page of orders and their items.
     *
     * @param flashSaleEventId event identifier retained in the route
     * @param page zero-based page number
     * @param size number of orders per page, from 1 through 100
     * @return the requested order page
     */
    @GetMapping("{flashSaleEventId}/orders")
    public OrderPageResponse getOrders(@PathVariable long flashSaleEventId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100");
        }
        return orders.getOrders(page, size);
    }

    /**
     * Returns an order and its items by order ID.
     *
     * @param flashSaleEventId event identifier retained in the route
     * @param orderId order identifier to retrieve
     * @return 200 with the order, or 404 when it does not exist
     */
    @GetMapping("{flashSaleEventId}/order/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable long flashSaleEventId,
                                                  @PathVariable long orderId) {
        var order = orders.getOrder(orderId);
        return ResponseEntity.of(order);
    }

    /**
     * Admits an order request through Redis inventory reservation and the outbox path.
     *
     * <p>A successful new reservation or matching idempotency replay returns 202 and does not
     * confirm that a database order has been created or Kafka delivery has completed.</p>
     *
     * @param flashSaleEventId flash-sale event identifier
     * @param flashSaleProductId event-product identifier
     * @param idempotencyKey request key used to prevent duplicate reservations
     * @param userId requesting user identifier
     * @return 202 with {@code PENDING} when admission succeeds, or a mapped error response
     */
    @PostMapping("/{flashSaleEventId}/product/{flashSaleProductId}")
    public ResponseEntity<?> placeOrder(
            @PathVariable long flashSaleEventId,
            @PathVariable long flashSaleProductId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("UserId") long userId) {
        if (flashSaleEventId <= 0 || flashSaleProductId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "flashSaleEventId and flashSaleProductId must be positive");
        }

        var result = orders.reserve(flashSaleEventId, flashSaleProductId, userId, idempotencyKey);
        return switch (result) {
            case RESERVED, REPLAY -> ResponseEntity.accepted().body(Map.of("status", "PENDING"));
            case LEGACY_REPLAY -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Legacy reservation delivery cannot be confirmed; manual reconciliation required"));
            case INVALID_ARGUMENT -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reservation request");
            case CONFLICT -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for another request");
            case OUT_OF_STOCK -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Product out of stock");
            case EVENT_NOT_RUNNING -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is not running");
            case MISSING_PRODUCT -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event product not found");
            case MISSING_EVENT, INVALID_STATE -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory unavailable");
        };
    }

}
