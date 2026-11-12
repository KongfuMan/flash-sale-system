package com.webapps.flashsale.inventory;

import com.webapps.flashsale.repository.FlashSaleEventProductRepository;
import com.webapps.flashsale.repository.FlashSaleEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

class InventoryMarkerPrefixTest {
    @Test
    void recognizesIdempotencyMarkersWithoutReservingAgain() throws Exception {
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
            owned = Boolean.TRUE.equals(redis.opsForHash().putIfAbsent(key, "status", "RUNNING"));
            assertTrue(owned);
            redis.opsForHash().put(key, "prod:1", "2");
            var inventory = new InventoryPrimitives(mock(FlashSaleEventRepository.class),
                    mock(FlashSaleEventProductRepository.class), redis);
            String request = "8c4eb4f3-860d-4da0-962d-15490e49dc7c";
            assertEquals(InventoryReservationResult.RESERVED, inventory.reserve(id, 1, 2, request));
            String field = "idem:" + request;
            var marker = redis.opsForHash().get(key, field);
            assertNotNull(marker);
            var mapper = new ObjectMapper();
            var compact = (ObjectNode) mapper.readTree(marker.toString());
            assertEquals(2, compact.path("version").asInt());
            assertFalse(compact.has("binding"));
            assertFalse(compact.has("message"));
            assertEquals(InventoryReservationResult.REPLAY, inventory.reserve(id, 1, 2, request));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 1, 3, request));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 2, 2, request));

            var legacy = compact.deepCopy();
            legacy.put("version", 1);
            var binding = legacy.putObject("binding");
            binding.set("eventId", legacy.get("eventId"));
            binding.set("eventProductId", legacy.get("eventProductId"));
            binding.set("userId", legacy.get("userId"));
            var message = binding.deepCopy();
            message.set("requestId", legacy.get("requestId"));
            legacy.set("message", message);
            redis.opsForHash().put(key, field, mapper.writeValueAsString(legacy));
            assertEquals(InventoryReservationResult.REPLAY, inventory.reserve(id, 1, 2, request));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 1, 3, request));

            message.put("userId", "wrong");
            redis.opsForHash().put(key, field, mapper.writeValueAsString(legacy));
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request));
            legacy.remove("binding");
            redis.opsForHash().put(key, field, mapper.writeValueAsString(legacy));
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request));
            compact.put("version", 3);
            redis.opsForHash().put(key, field, mapper.writeValueAsString(compact));
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request));
            compact.put("version", 2);
            compact.remove("requestId");
            redis.opsForHash().put(key, field, mapper.writeValueAsString(compact));
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request));
            assertEquals("1", redis.opsForHash().get(key, "prod:1"));
            redis.opsForHash().put(key, field, "{}");
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request));
            assertEquals("1", redis.opsForHash().get(key, "prod:1"));
        } finally {
            if (owned) redis.delete(key);
            factory.destroy();
        }
    }
}
