package com.webapps.flashsale.inventory;

import com.webapps.flashsale.repository.FlashSaleEventProductRepository;
import com.webapps.flashsale.repository.FlashSaleEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
            var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest("request".getBytes(StandardCharsets.UTF_8)));
            assertEquals(InventoryReservationResult.RESERVED, inventory.reserve(id, 1, 2, "request"));
            String field = "idem:" + digest;
            var marker = redis.opsForHash().get(key, field);
            assertNotNull(marker);
            assertEquals(InventoryReservationResult.REPLAY, inventory.reserve(id, 1, 2, "request"));
            assertEquals(InventoryReservationResult.CONFLICT, inventory.reserve(id, 1, 3, "request"));
            assertEquals("1", redis.opsForHash().get(key, "prod:1"));
            redis.opsForHash().put(key, field, "{}");
            assertEquals(InventoryReservationResult.INVALID_STATE, inventory.reserve(id, 1, 2, "request"));
            assertEquals("1", redis.opsForHash().get(key, "prod:1"));
        } finally {
            if (owned) redis.delete(key);
            factory.destroy();
        }
    }
}
