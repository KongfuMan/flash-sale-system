package com.webapps.flashsale.inventory;

import com.webapps.flashsale.entity.FlashSaleEventEntity;
import com.webapps.flashsale.entity.FlashSaleEventProductEntity;
import com.webapps.flashsale.repository.FlashSaleEventProductRepository;
import com.webapps.flashsale.repository.FlashSaleEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryClusterTest {
    @Test void isolatedClusterScripts() throws Exception {
        String nodes = System.getProperty("inventory.test.cluster.nodes");
        assumeTrue(nodes != null && !nodes.isBlank());
        var factory = new LettuceConnectionFactory(new RedisClusterConfiguration(List.of(nodes.split(","))));
        factory.afterPropertiesSet();
        factory.start();
        var redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        long id = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        String key = "flashsale:inventory:v1:{event:" + id + "}";
        boolean owned = false;
        try {
            var events = mock(FlashSaleEventRepository.class);
            var products = mock(FlashSaleEventProductRepository.class);
            var event = new FlashSaleEventEntity(); event.status = "UPCOMING";
            var row = new FlashSaleEventProductEntity();
            row.eventId = id; row.eventProductId = 1L; row.productId = 1L;
            row.allocatedQuantity = 20; row.soldQuantity = 0; row.reservedQuantity = 0;
            when(events.findById(id)).thenReturn(Optional.of(event));
            when(products.findByEventId(eq(id), any(Pageable.class))).thenReturn(List.of(row));
            var inventory = new InventoryPrimitives(events, products, redis);
            assertEquals(InventoryReservationResult.MISSING_EVENT, inventory.reserve(id, 1, 3, "first"));
            owned = inventory.prefill(id);
            assertTrue(owned, "Random key already exists; refusing to modify it");
            assertFalse(inventory.prefill(id));
            assertEquals(InventoryReservationResult.EVENT_NOT_RUNNING, inventory.reserve(id, 1, 3, "first"));
            event.status = "RUNNING";
            assertEquals(InventoryPrimitives.ActivationResult.ACTIVATED, inventory.activate(id));
            assertEquals("20", redis.opsForHash().get(key, "p:1"));
            assertEquals(InventoryReservationResult.RESERVED, inventory.reserve(id, 1, Long.MAX_VALUE, "first"));
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("first".getBytes(StandardCharsets.UTF_8)));
            String markerField = "i:" + digest;
            var marker = new ObjectMapper().readTree((String) redis.opsForHash().get(key, markerField));
            assertEquals(1, marker.path("version").asInt());
            assertEquals("first", marker.path("idempotencyKey").asText());
            assertEquals("PENDING", marker.path("status").asText());
            assertEquals(Long.toString(id), marker.path("eventId").asText());
            assertEquals("1", marker.path("binding").path("eventProductId").asText());
            assertEquals(Long.toString(Long.MAX_VALUE), marker.path("message").path("userId").asText());
            assertEquals(marker.path("requestId"), marker.path("message").path("requestId"));
            assertEquals(64, marker.path("requestId").asText().length());
            assertEquals(InventoryPrimitives.ActivationResult.ACTIVATED, inventory.activate(id));
            assertEquals("19", redis.opsForHash().get(key, "p:1"));
            redis.opsForHash().put(key, "status", "ENDED");
            assertEquals(InventoryReservationResult.REPLAY, inventory.reserve(id, 1, Long.MAX_VALUE, "first"));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 2, Long.MAX_VALUE, "first"));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 1, 3, "first"));
            assertEquals(InventoryPrimitives.ActivationResult.INVALID_STATE, inventory.activate(id));
            assertEquals("19", redis.opsForHash().get(key, "p:1"));
            assertEquals("ENDED", redis.opsForHash().get(key, "status"));
            redis.opsForHash().put(key, "status", "RUNNING");
            assertEquals(InventoryReservationResult.MISSING_PRODUCT, inventory.reserve(id, 2, 3, "other"));
            var sameKeyResults = new ArrayList<Future<InventoryReservationResult>>();
            var sameKeyPool = Executors.newFixedThreadPool(8);
            try {
                for (int i = 0; i < 40; i++) sameKeyResults.add(sameKeyPool.submit(() -> inventory.reserve(id, 1, 3, "concurrent")));
                int reserved = 0;
                for (var future : sameKeyResults) {
                    var result = future.get(30, TimeUnit.SECONDS);
                    if (result == InventoryReservationResult.RESERVED) reserved++;
                    else assertEquals(InventoryReservationResult.REPLAY, result);
                }
                assertEquals(1, reserved);
            } finally {
                sameKeyPool.shutdown();
                assertTrue(sameKeyPool.awaitTermination(30, TimeUnit.SECONDS));
            }
            var successes = new AtomicInteger();
            var pool = Executors.newFixedThreadPool(8);
            var results = new ArrayList<Future<?>>();
            try {
                for (int i = 0; i < 40; i++) {
                    String unique = "key-" + i;
                    results.add(pool.submit(() -> {
                        var result = inventory.reserve(id, 1, 3, unique);
                        if (result == InventoryReservationResult.RESERVED) successes.incrementAndGet();
                        else assertEquals(InventoryReservationResult.OUT_OF_STOCK, result);
                    }));
                }
                for (var result : results) result.get(30, TimeUnit.SECONDS);
            } finally {
                pool.shutdown();
                assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
            }
            assertEquals(18, successes.get());
            assertEquals(InventoryReservationResult.OUT_OF_STOCK, inventory.reserve(id, 1, 3, "out"));
            assertEquals(InventoryReservationResult.REPLAY, inventory.reserve(id, 1, 3, "concurrent"));
            assertFalse(redis.opsForHash().hasKey(key, "i:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("out".getBytes(StandardCharsets.UTF_8)))));
            var malformed = marker.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) malformed).put("eventId", id);
            redis.opsForHash().put(key, markerField, malformed.toString());
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, Long.MAX_VALUE, "first"));
            redis.opsForHash().put(key, markerField, "{}");
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, Long.MAX_VALUE, "first"));
            redis.opsForHash().put(key, "p:1", "invalid");
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 3, "invalid-stock"));
            assertEquals("invalid", redis.opsForHash().get(key, "p:1"));
            redis.delete(key);
            redis.opsForValue().set(key, "wrong-type");
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 3, "wrong-type"));
        } finally {
            if (owned) redis.delete(key);
            factory.destroy();
        }
    }
}
