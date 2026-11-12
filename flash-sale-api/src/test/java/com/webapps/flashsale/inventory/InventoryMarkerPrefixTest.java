package com.webapps.flashsale.inventory;

import com.webapps.flashsale.repository.FlashSaleEventProductRepository;
import com.webapps.flashsale.repository.FlashSaleEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

class InventoryMarkerPrefixTest {
    @Test
    void recognizesIdempotencyMarkersWithoutReservingAgain() throws Exception {
        String nodes = System.getProperty("inventory.test.cluster.nodes");
        if (Boolean.getBoolean("inventory.test.cluster.required")) {
            assertTrue(nodes != null && !nodes.isBlank(), "Redis cluster nodes are required");
        }
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
            String request = "key-\"-\\-\u00e9";
            String payload = "{\"message_id\":\"test\"}";
            assertEquals(InventoryReservationResult.RESERVED, inventory.reserve(id, 1, 2, request, payload));
            String field = "idem:" + request;
            var marker = redis.opsForHash().get(key, field);
            assertNotNull(marker);
            var mapper = new ObjectMapper();
            var compact = (ObjectNode) mapper.readTree(marker.toString());
            String requestId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((id + ":" + request).getBytes(StandardCharsets.UTF_8)));
            assertEquals(payload, redis.opsForHash().get(key, "outbox:" + requestId));
            assertEquals(mapper.valueToTree(InventoryMarker.pending(request, requestId, id, 1, 2)), compact);
            assertEquals(InventoryReservationResult.REPLAY, inventory.reserve(id, 1, 2, request, "different"));
            assertEquals(payload, redis.opsForHash().get(key, "outbox:" + requestId));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 1, 3, request, payload));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 2, 2, request, payload));

            redis.opsForHash().delete(key, "outbox:" + requestId);
            var legacyV2 = compact.deepCopy();
            legacyV2.put("version", 2);
            redis.opsForHash().put(key, field, mapper.writeValueAsString(legacyV2));
            var beforeReplay = redis.opsForHash().entries(key);
            assertEquals(InventoryReservationResult.LEGACY_REPLAY, inventory.reserve(id, 1, 2, request, payload));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 1, 3, request, payload));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 2, 2, request, payload));
            assertEquals(beforeReplay, redis.opsForHash().entries(key));

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
            beforeReplay = redis.opsForHash().entries(key);
            assertEquals(InventoryReservationResult.LEGACY_REPLAY, inventory.reserve(id, 1, 2, request, payload));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 1, 3, request, payload));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 2, 2, request, payload));
            assertEquals(beforeReplay, redis.opsForHash().entries(key));

            message.put("userId", "wrong");
            redis.opsForHash().put(key, field, mapper.writeValueAsString(legacy));
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request, payload));
            legacy.remove("binding");
            redis.opsForHash().put(key, field, mapper.writeValueAsString(legacy));
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request, payload));
            compact.put("version", 4);
            redis.opsForHash().put(key, field, mapper.writeValueAsString(compact));
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request, payload));
            compact.put("version", 3);
            compact.remove("requestId");
            redis.opsForHash().put(key, field, mapper.writeValueAsString(compact));
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request, payload));
            assertEquals("1", redis.opsForHash().get(key, "prod:1"));

            var script = new DefaultRedisScript<String>();
            script.setLocation(new ClassPathResource("redis/reserve.lua"));
            script.setResultType(String.class);
            String otherKey = "other-key";
            String valid = mapper.writeValueAsString(InventoryMarker.pending(otherKey, "request-id", id, 1, 2));
            for (String invalid : List.of("not-json", "{}", valid.replace("\"userId\":\"2\"", "\"userId\":\"3\""))) {
                assertEquals("INVALID_STATE", redis.execute(script, List.of(key), Long.toString(id), "1", "2",
                        otherKey, "request-id", invalid, payload));
                assertFalse(redis.opsForHash().hasKey(key, "idem:" + otherKey));
                assertEquals("1", redis.opsForHash().get(key, "prod:1"));
            }
            redis.opsForHash().put(key, field, "{}");
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, request, payload));
            assertEquals("1", redis.opsForHash().get(key, "prod:1"));
            redis.opsForHash().put(key, "outbox:request-id", "existing");
            assertEquals("INVALID_STATE", redis.execute(script, List.of(key), Long.toString(id), "1", "2",
                    otherKey, "request-id", valid, payload));
            assertEquals("existing", redis.opsForHash().get(key, "outbox:request-id"));
            assertFalse(redis.opsForHash().hasKey(key, "idem:" + otherKey));
            assertEquals("1", redis.opsForHash().get(key, "prod:1"));

            var prefill = new DefaultRedisScript<Long>();
            prefill.setLocation(new ClassPathResource("redis/prefill.lua"));
            prefill.setResultType(Long.class);
            var before = redis.opsForHash().entries(key);
            assertEquals(0L, redis.execute(prefill, List.of(key), "status", "UPCOMING", "prod:1", "99"));
            assertEquals(before, redis.opsForHash().entries(key));
        } finally {
            if (owned) redis.delete(key);
            factory.destroy();
        }
    }
}
