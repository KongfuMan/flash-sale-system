package com.webapps.flashsale.service;

import com.webapps.flashsale.dto.PaymentRequest;
import com.webapps.flashsale.dto.PaymentResponse;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
    private final NamedParameterJdbcTemplate jdbc;

    public PaymentService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public PaymentResponse pay(long orderId, PaymentRequest request) {
        var orders = jdbc.query("""
                SELECT event_id, event_product_id, status, expires_at
                FROM orders WHERE order_id = :orderId FOR UPDATE
                """, Map.of("orderId", orderId), (rs, row) -> new Order(
                rs.getLong("event_id"), rs.getLong("event_product_id"), rs.getString("status")));
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        Order order = orders.get(0);
        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not pending payment");
        }
        var params = new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("eventId", order.eventId())
                .addValue("eventProductId", order.eventProductId())
                .addValue("paymentId", request.paymentId())
                .addValue("transactionId", request.transactionId())
                .addValue("paymentMethod", request.paymentMethod());

        // Evaluate wall-clock time after acquiring the order lock, not at transaction start.
        int eligible = jdbc.update("""
                UPDATE orders SET status = 'PAID'
                WHERE order_id = :orderId AND status = 'PENDING_PAYMENT'
                  AND expires_at > clock_timestamp()
                """, params);
        if (eligible != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order has expired or is not pending payment");
        }
        var prices = jdbc.queryForList("""
                UPDATE flash_sale_event_products
                SET reserved_quantity = reserved_quantity - 1, sold_quantity = sold_quantity + 1
                WHERE event_id = :eventId AND event_product_id = :eventProductId
                  AND reserved_quantity >= 1
                RETURNING flash_price
                """, params, BigDecimal.class);
        if (prices.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved inventory unavailable");
        }
        BigDecimal amount = prices.get(0);
        params.addValue("amount", amount);
        jdbc.update("""
                INSERT INTO order_payment (payment_id, order_id, amount, payment_method, status, transaction_id, paid_at)
                VALUES (:paymentId, :orderId, :amount, :paymentMethod, 'SUCCESS', :transactionId, clock_timestamp())
                """, params);
        return new PaymentResponse(request.paymentId(), orderId, request.transactionId(), amount, "SUCCESS");
    }

    private record Order(
            long eventId,
            long eventProductId,
            String status) {}
}
