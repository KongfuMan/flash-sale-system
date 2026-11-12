package com.webapps.flashsale.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webapps.flashsale.entity.FlashSaleEventProductEntity;
import com.webapps.flashsale.repository.FlashSaleEventProductRepository;
import com.webapps.flashsale.repository.FlashSaleEventRepository;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Experimental Redis inventory primitives; not connected to database order processing. */
@Service
public class InventoryPrimitives {
    static final int MAX_PRODUCTS = 1000;
    private static final ObjectMapper MARKER_MAPPER = new ObjectMapper();
    private static final DefaultRedisScript<Long> PREFILL = script("prefill");
    private static final DefaultRedisScript<Long> ACTIVATE = script("activate");
    private static final DefaultRedisScript<String> RESERVE = reserveScript();

    private static DefaultRedisScript<String> reserveScript() {
        var script = new DefaultRedisScript<String>();
        script.setLocation(new ClassPathResource("redis/reserve.lua"));
        script.setResultType(String.class);
        return script;
    }

    private static DefaultRedisScript<Long> script(String name) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("redis/" + name + ".lua"));
        script.setResultType(Long.class);
        return script;
    }

    public enum ActivationResult { ACTIVATED, MISSING_EVENT, INVALID_STATE }

    private final FlashSaleEventRepository events;
    private final FlashSaleEventProductRepository products;
    private final StringRedisTemplate redis;

    public InventoryPrimitives(FlashSaleEventRepository events, FlashSaleEventProductRepository products, StringRedisTemplate redis) {
        this.events = events;
        this.products = products;
        this.redis = redis;
    }

    public boolean prefill(long flashSaleEventId) {
        String key = key(flashSaleEventId);
        var event = events.findById(flashSaleEventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        var eventProducts = products.findByEventId(flashSaleEventId, PageRequest.of(0, MAX_PRODUCTS + 1));
        if (eventProducts.isEmpty() || eventProducts.size() > MAX_PRODUCTS) {
            throw new IllegalArgumentException("Event must have between 1 and " + MAX_PRODUCTS + " products");
        }

        List<String> args = new ArrayList<>();
        args.add("status");
        args.add(event.status);
        for (FlashSaleEventProductEntity row : eventProducts) {
            long available = (long) row.allocatedQuantity - row.soldQuantity - row.reservedQuantity;
            args.add("prod:" + row.eventProductId);
            args.add(Long.toString(available));
        }
        Long result = redis.execute(PREFILL, List.of(key), args.toArray());
        return result == 1;
    }

    public InventoryReservationResult reserve(long flashSaleEventId, long productId, long userId, String idempotencyKey, String payload) {
        if (flashSaleEventId <= 0 || productId <= 0 || userId <= 0 || idempotencyKey == null
                || idempotencyKey.isBlank() || idempotencyKey.length() > 128 || payload == null || payload.isBlank()) {
            return InventoryReservationResult.INVALID_ARGUMENT;
        }

        String key = key(flashSaleEventId);
        String requestId = sha256(flashSaleEventId + ":" + idempotencyKey);
        String marker;
        try {
            marker = MARKER_MAPPER.writeValueAsString(InventoryMarker.pending(
                    idempotencyKey, requestId, flashSaleEventId, productId, userId));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize inventory marker", ex);
        }
        String result = redis.execute(RESERVE, List.of(key), Long.toString(flashSaleEventId), Long.toString(productId),
                Long.toString(userId), idempotencyKey, requestId, marker, payload);

        try {
            var parsed = InventoryReservationResult.valueOf(result);
            if (parsed == InventoryReservationResult.INVALID_ARGUMENT) {
                throw new IllegalArgumentException();
            }

            return parsed;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unknown reserve result: " + result, ex);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Operator call activate to turn the status of inactive event into RUNNING in redis.
     * Caller must keep event writers quiescent across the database read and Redis update.
     * */
    public ActivationResult activate(long eventId) {
        String key = key(eventId);
        var event = events.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        if (!"RUNNING".equals(event.status)) {
            throw new IllegalStateException("Database event is not RUNNING");
        }

        Long result = redis.execute(ACTIVATE, List.of(key));
        return switch (result.intValue()) {
            case 1 -> ActivationResult.ACTIVATED;
            case -1 -> ActivationResult.MISSING_EVENT;
            case -2 -> ActivationResult.INVALID_STATE;
            default -> throw new IllegalStateException("Unknown activation result: " + result);
        };
    }

    private static String key(long eventId) {
        if (eventId <= 0) throw new IllegalArgumentException("Invalid event ID");
        return "flashsale:inventory:v1:{event:" + eventId + "}";
    }
}
