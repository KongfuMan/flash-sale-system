package com.webapps.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.webapps.flashsale.contract.MessageEnvelope;
import com.webapps.flashsale.contract.MessageType;
import com.webapps.flashsale.contract.OrderRequested;
import com.webapps.flashsale.service.ReservationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ReservationListenerTest {
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final ReservationService reservations = mock(ReservationService.class);
    private final ReservationListener listener = new ReservationListener(JsonMapper.builder().findAndAddModules().build(), reservations);
    private static final String MESSAGE = """
            {"message_id":"8c4eb4f3-860d-4da0-962d-15490e49dc7c","message_type":"ORDER_REQUESTED",
             "schema_version":2,"occurred_at":"2026-11-05T20:36:46.115907+00:00",
             "data":{"flash_sale_event_id":1,"user_id":3,"items":[{"event_product_id":2,"quantity":1}]}}
            """;

    @Test
    void acknowledgesAfterReservation() throws Exception {
        when(reservations.reserve(1, 2, 3, "kafka:8c4eb4f3-860d-4da0-962d-15490e49dc7c"))
                .thenReturn(new ReservationService.Result(42, true));
        listener.receive(MESSAGE, acknowledgment);
        var order = inOrder(reservations, acknowledgment);
        order.verify(reservations).reserve(1, 2, 3, "kafka:8c4eb4f3-860d-4da0-962d-15490e49dc7c");
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    void acceptsProducerSerializedSharedContractAndReplay() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var message = new MessageEnvelope<>(UUID.fromString("8c4eb4f3-860d-4da0-962d-15490e49dc7c"),
                MessageType.ORDER_REQUESTED, 2, OffsetDateTime.parse("2026-11-05T20:36:46Z"),
                new OrderRequested(1L, 3L, List.of(new OrderRequested.Item(2L, 1))));
        when(reservations.reserve(1, 2, 3, "kafka:8c4eb4f3-860d-4da0-962d-15490e49dc7c"))
                .thenReturn(new ReservationService.Result(42, false));
        listener.receive(mapper.writeValueAsString(message), acknowledgment);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void rejectsMultiItemV2WithoutAcknowledging() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var message = new MessageEnvelope<>(UUID.randomUUID(), MessageType.ORDER_REQUESTED, 2,
                OffsetDateTime.parse("2026-11-05T20:36:46Z"),
                new OrderRequested(1L, 3L, List.of(
                        new OrderRequested.Item(2L, 1), new OrderRequested.Item(4L, 2))));
        var json = mapper.writeValueAsString(message);
        var payload = mapper.readTree(json).path("data");
        assertEquals(2, payload.path("items").size());
        assertEquals(4L, payload.path("items").get(1).path("event_product_id").asLong());
        assertEquals(message, mapper.readValue(json, new TypeReference<MessageEnvelope<OrderRequested>>() {}));
        assertThrows(IllegalArgumentException.class, () -> listener.receive(json, acknowledgment));
        verifyNoInteractions(acknowledgment, reservations);
    }

    @Test
    void doesNotAcknowledgeReservationFailure() {
        when(reservations.reserve(anyLong(), anyLong(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("database failure"));
        assertThrows(IllegalStateException.class, () -> listener.receive(MESSAGE, acknowledgment));
        verifyNoInteractions(acknowledgment);
    }

    @Test
    void rejectsInvalidAndUnsupportedEventsWithoutAcknowledging() {
        for (String message : new String[]{"{", "null", "{}",
                MESSAGE.replace("ORDER_REQUESTED", "ORDER_CREATED"),
                MESSAGE.replace("ORDER_REQUESTED", "UNKNOWN"),
                MESSAGE.replace("\"user_id\":3", "\"user_id\":3,\"order_id\":42"),
                MESSAGE.replace("\"schema_version\":2", "\"schema_version\":1"),
                MESSAGE.replace("\"schema_version\":2", "\"schema_version\":\"2\""),
                MESSAGE.replace("\"user_id\":3", "\"user_id\":0"),
                MESSAGE.replace("\"quantity\":1", "\"quantity\":2"),
                MESSAGE.replace("\"quantity\":1", "\"quantity\":1.5"),
                MESSAGE.replace("[{\"event_product_id\":2,\"quantity\":1}]", "[]"),
                MESSAGE.replace("[{\"event_product_id\":2,\"quantity\":1}]", "[null]")}) {
            assertThrows(Exception.class, () -> listener.receive(message, acknowledgment));
        }
        verifyNoInteractions(acknowledgment);
        verifyNoInteractions(reservations);
    }
}
