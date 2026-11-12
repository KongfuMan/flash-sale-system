package com.webapps.flashsale.relay;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class RelayWorkerTest {
    private static final String ID = "8c4eb4f3-860d-4da0-962d-15490e49dc7c";
    private static final String MESSAGE = "{\"message_id\":\"" + ID + "\",\"message_type\":\"ORDER_REQUESTED\",\"schema_version\":2,\"occurred_at\":\"2026-11-05T20:36:46Z\",\"data\":{\"flash_sale_event_id\":1,\"user_id\":3,\"items\":[{\"event_product_id\":2,\"quantity\":1}]}}";
    private final RelayWorker worker = new RelayWorker(JsonMapper.builder().findAndAddModules().build(), null, null, "orders", 10);
    @SuppressWarnings("unchecked") private final StatefulRedisClusterConnection<String, String> redis = mock(StatefulRedisClusterConnection.class);
    @SuppressWarnings("unchecked") private final RedisAdvancedClusterCommands<String, String> commands = mock(RedisAdvancedClusterCommands.class);
    @SuppressWarnings("unchecked") private final Producer<String, String> producer = mock(Producer.class);

    @Test void deletesExactValueOnlyAfterAcknowledgement() {
        when(redis.sync()).thenReturn(commands);
        when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(mock(RecordMetadata.class)));
        worker.relay(redis, producer, "key", "field", MESSAGE);
        var order = inOrder(producer, commands);
        order.verify(producer).send(argThat((ProducerRecord<String, String> r) -> r.topic().equals("orders") && r.key().equals(ID) && r.value().equals(MESSAGE)));
        order.verify(commands).eval(eq(RelayWorker.DELETE), eq(ScriptOutputType.INTEGER), aryEq(new String[]{"key"}), eq("field"), eq(MESSAGE));
        assertTrue(RelayWorker.DELETE.contains("redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2]"));
        assertTrue(RelayWorker.DELETE.contains("redis.call('HDEL', KEYS[1], ARGV[1])"));
    }

    @Test void failuresAndTimeoutNeverDelete() {
        when(producer.send(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException()), new CompletableFuture<>());
        worker.relay(redis, producer, "key", "field", MESSAGE);
        worker.relay(redis, producer, "key", "field", MESSAGE);
        verifyNoInteractions(redis);
    }

    @Test void acceptsLongIdBoundaryAndOffsetTimestamp() {
        when(redis.sync()).thenReturn(commands);
        when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(mock(RecordMetadata.class)));
        String message = MESSAGE.replace("\"user_id\":3", "\"user_id\":9223372036854775807")
                .replace("2026-11-05T20:36:46Z", "2026-11-05T20:36:46.115907+05:30");
        worker.relay(redis, producer, "key", "field", message);
        verify(producer).send(argThat((ProducerRecord<String, String> r) -> r.key().equals(ID) && r.value().equals(message)));
        verify(commands).eval(eq(RelayWorker.DELETE), eq(ScriptOutputType.INTEGER), aryEq(new String[]{"key"}), eq("field"), eq(message));
    }

    @Test void malformedEnvelopeNeverPublished() {
        for (String value : new String[]{"{", "null", "{}", MESSAGE + " {}", MESSAGE.replace(ID, "bad"),
                MESSAGE.replace(ID, ID.toUpperCase()), MESSAGE.replace("2026-11-05T20:36:46Z", "bad"),
                MESSAGE.replace("ORDER_REQUESTED", "ORDER_CREATED"), MESSAGE.replace("ORDER_REQUESTED", "UNKNOWN"),
                MESSAGE.replace("\"schema_version\":2", "\"schema_version\":1"),
                MESSAGE.replace("\"schema_version\":2", "\"schema_version\":\"2\""),
                MESSAGE.replace("\"schema_version\":2", "\"schema_version\":2,\"schema_version\":2"),
                MESSAGE.replace("\"message_type\":", "\"extra\":1,\"message_type\":"),
                MESSAGE.replace("\"user_id\":3", "\"user_id\":3,\"extra\":1"),
                MESSAGE.replace("\"quantity\":1", "\"quantity\":1,\"extra\":1"),
                MESSAGE.replace("\"flash_sale_event_id\":1", "\"flash_sale_event_id\":0"),
                MESSAGE.replace("\"flash_sale_event_id\":1", "\"flash_sale_event_id\":9223372036854775808"),
                MESSAGE.replace("\"user_id\":3", "\"user_id\":\"3\""),
                MESSAGE.replace("\"user_id\":3", "\"user_id\":-1"),
                MESSAGE.replace("\"event_product_id\":2", "\"event_product_id\":2.0"),
                MESSAGE.replace("\"event_product_id\":2", "\"event_product_id\":null"),
                MESSAGE.replace("\"quantity\":1", "\"quantity\":1.0"),
                MESSAGE.replace("\"quantity\":1", "\"quantity\":2"),
                MESSAGE.replace("[{\"event_product_id\":2,\"quantity\":1}]", "[]"),
                MESSAGE.replace("[{\"event_product_id\":2,\"quantity\":1}]", "[null]"),
                MESSAGE.replace("[{\"event_product_id\":2,\"quantity\":1}]", "[{\"event_product_id\":2,\"quantity\":1},{\"event_product_id\":2,\"quantity\":1}]"),
                MESSAGE.replace("{\"flash_sale_event_id\":1,\"user_id\":3,\"items\":[{\"event_product_id\":2,\"quantity\":1}]}", "{}")}) {
            worker.relay(redis, producer, "key", "field", value);
        }
        verifyNoInteractions(producer, redis);
    }
}
