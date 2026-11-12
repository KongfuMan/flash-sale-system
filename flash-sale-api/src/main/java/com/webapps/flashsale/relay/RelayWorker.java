package com.webapps.flashsale.relay;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webapps.flashsale.contract.MessageEnvelope;
import com.webapps.flashsale.contract.OrderRequested;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class RelayWorker implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(RelayWorker.class);
    private static final TypeReference<MessageEnvelope<OrderRequested>> REQUEST_TYPE = new TypeReference<>() {};
    static final String DELETE = "if redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2] then return redis.call('HDEL', KEYS[1], ARGV[1]) else return 0 end";
    private final ObjectMapper mapper;
    private final String topic;
    private final long timeoutMs;
    private final LettuceConnectionFactory redisFactory;
    private final ProducerFactory<String, String> producerFactory;

    RelayWorker(ObjectMapper mapper, LettuceConnectionFactory redisFactory, ProducerFactory<String, String> producerFactory,
                @Value("${relay.topic:order-requests-v1}") String topic,
                @Value("${relay.timeout-ms:5000}") long timeoutMs) {
        if (timeoutMs <= 0 || topic.isBlank()) throw new IllegalArgumentException("Invalid relay configuration");
        if (redisFactory != null && !redisFactory.isClusterAware()) throw new IllegalArgumentException("Relay requires Redis Cluster configuration");
        this.mapper = mapper; this.redisFactory = redisFactory;
        this.topic = topic; this.timeoutMs = timeoutMs;
        this.producerFactory = producerFactory;
    }

    @Scheduled(fixedDelayString = "${relay.poll-ms:1000}")
    public void run() {
        try (StatefulRedisClusterConnection<String, String> redis = ((RedisClusterClient) redisFactory.getRequiredNativeClient()).connect();
             Producer<String, String> producer = producerFactory.createNonTransactionalProducer()) {
            scan(redis, producer);
        } catch (Exception e) {
            log.warn("Relay scan failed; retrying", e);
        }
    }

    void scan(StatefulRedisClusterConnection<String, String> redis, Producer<String, String> producer) {
        for (RedisClusterNode node : redis.getPartitions()) {
            if (Thread.currentThread().isInterrupted()) return;
            if (!node.is(RedisClusterNode.NodeFlag.MASTER) || node.is(RedisClusterNode.NodeFlag.FAIL)) continue;
            try {
                var nodeCommands = redis.getConnection(node.getNodeId()).sync();
                ScanCursor cursor = ScanCursor.INITIAL;
                do {
                    var page = nodeCommands.scan(cursor, ScanArgs.Builder.matches("flashsale:inventory:v1:{event:*}").limit(100));
                    for (String key : page.getKeys()) {
                        if (Thread.currentThread().isInterrupted()) return;
                        try { scanHash(redis, producer, key); }
                        catch (Exception e) { log.warn("Relay hash scan failed; retrying"); }
                    }
                    cursor = page;
                } while (!cursor.isFinished());
            } catch (Exception e) { log.warn("Relay primary scan failed; retrying"); }
        }
    }

    private void scanHash(StatefulRedisClusterConnection<String, String> redis, Producer<String, String> producer, String key) {
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            var page = redis.sync().hscan(key, cursor, ScanArgs.Builder.matches("outbox:*").limit(100));
            for (Map.Entry<String, String> entry : page.getMap().entrySet()) {
                if (Thread.currentThread().isInterrupted()) return;
                relay(redis, producer, key, entry.getKey(), entry.getValue());
            }
            cursor = page;
        } while (!cursor.isFinished());
    }

    void relay(StatefulRedisClusterConnection<String, String> redis, Producer<String, String> producer,
               String key, String field, String value) {
        String id;
        try {
            JsonNode json = mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
            if (json == null || !json.isObject() || !json.path("message_id").isTextual()
                    || !json.path("message_type").isTextual() || !"ORDER_REQUESTED".equals(json.path("message_type").textValue())
                    || !json.path("schema_version").isIntegralNumber() || !json.path("schema_version").canConvertToInt()
                    || json.path("schema_version").intValue() != 2
                    || !json.path("occurred_at").isTextual() || !json.path("data").isObject()) {
                throw new IllegalArgumentException("Invalid envelope");
            }
            id = json.path("message_id").asText();
            if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid UUID");
            OffsetDateTime.parse(json.path("occurred_at").textValue());
            JsonNode data = json.path("data");
            JsonNode items = data.path("items");
            if (!positiveLong(data.path("flash_sale_event_id")) || !positiveLong(data.path("user_id"))
                    || !items.isArray() || items.size() != 1 || !items.get(0).isObject()
                    || !positiveLong(items.get(0).path("event_product_id"))
                    || !items.get(0).path("quantity").isIntegralNumber()
                    || !items.get(0).path("quantity").canConvertToInt()
                    || items.get(0).path("quantity").intValue() != 1) {
                throw new IllegalArgumentException("Invalid order request");
            }
            MessageEnvelope<OrderRequested> event = mapper.readerFor(REQUEST_TYPE)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(json);
            if (event.messageId() == null || event.occurredAt() == null || event.data() == null) {
                throw new IllegalArgumentException("Invalid envelope");
            }
        } catch (Exception e) {
            log.warn("Relay envelope validation failed; record retained for retry");
            return;
        }
        try {
            producer.send(new ProducerRecord<>(topic, id, value)).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            log.warn("Relay publish failed or timed out; record retained for retry and delivery may be duplicated");
            return;
        }
        try { redis.sync().eval(DELETE, ScriptOutputType.INTEGER, new String[]{key}, field, value); }
        catch (Exception e) { log.warn("Relay conditional deletion failed or timed out; deletion outcome may be uncertain"); }
    }

    private static boolean positiveLong(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0;
    }

    @Override
    public void close() {
        if (producerFactory instanceof DefaultKafkaProducerFactory<?, ?> factory) factory.destroy();
    }

}
