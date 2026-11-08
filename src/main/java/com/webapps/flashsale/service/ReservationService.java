package com.webapps.flashsale.service;

import java.math.BigDecimal;
import java.util.Map;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {
    public static final String EventId = "eventId";
    public static final String EventProductId = "eventProductId";
    public static final String UserId = "userId";
    public static final String IdempotencyKey = "idempotencyKey";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public ReservationService(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Result reserve(long eventId, long eventProductId, long userId, String idempotencyKey) {
        var params = Map.of(
                EventId, eventId,
                EventProductId, eventProductId,
                UserId, userId,
                IdempotencyKey, idempotencyKey);

        try {
            return transaction.execute(status -> {
                // step 0. check dup order
                var existing = findExisting(params);
                if (existing != null) {
                    return existing;
                }

                // step 1. check existing running event with read lock
                var events = jdbc.queryForList("""
                    SELECT event_id
                    FROM flash_sale_events
                    WHERE event_id = :eventId AND status = 'RUNNING'
                    FOR SHARE""", params, Long.class);

                if (events.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event does not exist or is not running");
                }

                // step 2. update inventory and place an order with order items.
                var reserved = jdbc.query("""
                        UPDATE flash_sale_event_products
                        SET reserved_quantity = reserved_quantity + 1
                        WHERE event_id = :eventId AND event_product_id = :eventProductId
                          AND sold_quantity + reserved_quantity < allocated_quantity
                        RETURNING product_id, flash_price
                        """, params, (rs, row) -> new Snapshot(rs.getLong("product_id"), rs.getBigDecimal("flash_price")));

                if (reserved.isEmpty()) {
                    var concurrent = findExisting(params);
                    if (concurrent != null) {
                        return concurrent;
                    }
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Product unavailable or out of stock");
                }
                if (reserved.size() != 1) {
                    throw new IllegalStateException("Unexpected inventory update count: " + reserved.size());
                }

                var orderIds = jdbc.queryForList("""
                        INSERT INTO orders (user_id, event_id, event_product_id, status, idempotency_key, expires_at)
                        VALUES (:userId, :eventId, :eventProductId, 'PENDING_PAYMENT',
                                :idempotencyKey, CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                        RETURNING order_id
                        """, params, Long.class);
                if (orderIds.size() != 1) {
                    throw new IllegalStateException("Unexpected order insert count: " + orderIds.size());
                }

                var itemParams = new MapSqlParameterSource(params)
                        .addValue("orderId", orderIds.get(0))
                        .addValue("productId", reserved.get(0).productId())
                        .addValue("flashPrice", reserved.get(0).flashPrice());
                int items = jdbc.update("""
                        INSERT INTO order_items (order_id, product_id, event_product_id, unit_price_snapshot, quantity)
                        VALUES (:orderId, :productId, :eventProductId, :flashPrice, 1)
                        """, itemParams);
                if (items != 1) {
                    throw new IllegalStateException("Unexpected order item insert count: " + items);
                }
                return new Result(orderIds.get(0), true);
            });
        } catch (DataAccessException ex) {
            if (!isIdempotencyConflict(ex)) {
                throw ex;
            }

            // TransactionTemplate has rolled back the inventory update before this lookup.
            Result existing = transaction.execute(status -> findExisting(params));

            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    /**
     * Check the dup order and if relevant columns match.
    * */
    private Result findExisting(Map<String, ?> params) {
        var rows = jdbc.query("""
                SELECT o.order_id, o.event_id, o.user_id, o.event_product_id
                FROM orders o
                WHERE o.idempotency_key = :idempotencyKey
                """, params, (rs, row) -> {
            boolean matches = rs.getLong("event_id") == (long) params.get(EventId) && !rs.wasNull()
                    && rs.getLong("user_id") == (long) params.get(UserId) && !rs.wasNull()
                    && rs.getLong("event_product_id") == (long) params.get(EventProductId) && !rs.wasNull();
            if (!matches) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for another request");
            }
            return new Result(rs.getLong("order_id"), false);
        });
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean isIdempotencyConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException pg && "23505".equals(pg.getSQLState())
                    && pg.getServerErrorMessage() != null
                    && "orders_idempotency_key_key".equals(pg.getServerErrorMessage().getConstraint())) return true;
        }
        return false;
    }

    private record Snapshot(long productId, BigDecimal flashPrice) {}

    public record Result(long orderId, boolean created) {}
}
