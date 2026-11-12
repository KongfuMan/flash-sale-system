package com.webapps.flashsale.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Run with -Drelay.redis.integration=true; relay.redis.uri optionally overrides the seed URI.
@EnabledIfSystemProperty(named = "relay.redis.integration", matches = "true")
class RelayWorkerClusterTest {
    private static final String FIELD = "outbox:" + "a".repeat(64);

    @Test
    void scansAllPrimariesAndRetriesWithoutDeletingChangedValues() throws Exception {
        RedisURI uri = RedisURI.create(System.getProperty("relay.redis.uri", "redis://localhost:7001"));
        uri.setTimeout(Duration.ofSeconds(2));
        RedisClusterClient client = RedisClusterClient.create(uri);
        try (StatefulRedisClusterConnection<String, String> redis = client.connect()) {
            Map<String, String> keysByNode = new HashMap<>();
            for (int i = 0; i < 1000 && keysByNode.size() < 3; i++) {
                String key = "flashsale:inventory:v1:{event:" + UUID.randomUUID() + "}";
                var node = redis.getPartitions().getMasterBySlot(SlotHash.getSlot(key));
                assertNotNull(node, "Cluster must cover all hash slots");
                keysByNode.putIfAbsent(node.getNodeId(), key);
            }
            assertEquals(3, keysByNode.size(), "Test requires at least three Redis cluster primaries");
            var keys = keysByNode.values().stream().toList();
            var allowed = new ConcurrentHashMap<String, CompletableFuture<RecordMetadata>>();
            var sends = new ConcurrentHashMap<String, AtomicInteger>();
            @SuppressWarnings("unchecked") Producer<String, String> producer = mock(Producer.class);
            when(producer.send(any())).thenAnswer(invocation -> {
                ProducerRecord<String, String> record = invocation.getArgument(0);
                CompletableFuture<RecordMetadata> result = allowed.get(record.value());
                if (result == null) return CompletableFuture.failedFuture(new IllegalStateException("Not a test record"));
                assertEquals("integration-orders", record.topic());
                assertEquals(new ObjectMapper().readTree(record.value()).path("message_id").asText(), record.key());
                sends.computeIfAbsent(record.value(), ignored -> new AtomicInteger()).incrementAndGet();
                return result;
            });
            RelayWorker worker = new RelayWorker(JsonMapper.builder().findAndAddModules().build(), null, null,
                    "integration-orders", 50);
            try {
                for (String key : keys) {
                    String message = message();
                    allowed.put(message, CompletableFuture.completedFuture(mock(RecordMetadata.class)));
                    redis.sync().hset(key, FIELD, message);
                    redis.sync().hset(key, "status", "test-owned");
                }
                worker.scan(redis, producer);
                assertTrue(keys.stream().allMatch(key -> redis.sync().hget(key, FIELD) == null));
                assertEquals(3, sends.size(), "Each primary's record should have been discovered");
                for (String key : keys) assertEquals("test-owned", redis.sync().hget(key, "status"));

                String timeout = message();
                var delayedAck = new CompletableFuture<RecordMetadata>();
                allowed.put(timeout, delayedAck);
                redis.sync().hset(keys.get(0), FIELD, timeout);
                worker.scan(redis, producer);
                worker.scan(redis, producer);
                assertTrue(sends.get(timeout).get() >= 2);
                assertEquals(timeout, redis.sync().hget(keys.get(0), FIELD), "Timeout must preserve the record");
                delayedAck.complete(mock(RecordMetadata.class));
                worker.scan(redis, producer);
                assertNull(redis.sync().hget(keys.get(0), FIELD));

                String original = message();
                String replacement = message();
                doAnswer(invocation -> {
                    ProducerRecord<String, String> record = invocation.getArgument(0);
                    if (record.value().equals(original)) {
                        redis.sync().hset(keys.get(1), FIELD, replacement);
                        return CompletableFuture.completedFuture(mock(RecordMetadata.class));
                    }
                    return CompletableFuture.failedFuture(new IllegalStateException());
                }).when(producer).send(any());
                redis.sync().hset(keys.get(1), FIELD, original);
                worker.scan(redis, producer);
                assertEquals(replacement, redis.sync().hget(keys.get(1), FIELD), "Compare/delete must retain a changed value");
            } finally {
                for (String key : keys) redis.sync().del(key);
            }
        } finally {
            client.shutdown();
        }
    }

    private static String message() {
        return "{\"message_id\":\"" + UUID.randomUUID() + "\",\"message_type\":\"ORDER_REQUESTED\",\"schema_version\":2,\"occurred_at\":\"2026-11-05T20:36:46Z\",\"data\":{\"flash_sale_event_id\":1,\"user_id\":3,\"items\":[{\"event_product_id\":2,\"quantity\":1}]}}";
    }

}
