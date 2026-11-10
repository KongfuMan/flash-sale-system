package com.webapps.consumer;

import com.webapps.consumer.service.OrderService;
import com.webapps.flashsale.contract.MessageEnvelope;
import com.webapps.flashsale.contract.MessageType;
import com.webapps.flashsale.contract.OrderRequested;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderRequestHandlingTest {
    private final OrderService service = spy(new OrderService(mock(NamedParameterJdbcTemplate.class),
            mock(PlatformTransactionManager.class)));
    private final UUID id = UUID.fromString("8c4eb4f3-860d-4da0-962d-15490e49dc7c");

    @Test
    void reservesUsingMessageIdAsIdempotencyKey() {
        doReturn(new OrderService.Result(42, true)).when(service)
                .reserve(1, 2, 3, "kafka:" + id);
        service.handleOrderRequested(event(new OrderRequested(1L, 3L, List.of(new OrderRequested.Item(2L, 1)))));
        verify(service).reserve(1, 2, 3, "kafka:" + id);
    }

    @Test
    void rejectsInvalidAndMultiItemRequests() {
        var requests = Arrays.asList(
                new OrderRequested(1L, 0L, List.of(new OrderRequested.Item(2L, 1))),
                new OrderRequested(1L, 3L, List.of()),
                new OrderRequested(1L, 3L, Arrays.asList((OrderRequested.Item) null)),
                new OrderRequested(1L, 3L, List.of(new OrderRequested.Item(2L, 2))),
                new OrderRequested(1L, 3L, List.of(new OrderRequested.Item(2L, 1), new OrderRequested.Item(4L, 1))));
        for (var request : requests) {
            assertThrows(IllegalArgumentException.class, () -> service.handleOrderRequested(event(request)));
        }
        verify(service, never()).reserve(anyLong(), anyLong(), anyLong(), anyString());
    }

    private MessageEnvelope<OrderRequested> event(OrderRequested request) {
        return new MessageEnvelope<>(id, MessageType.ORDER_REQUESTED, 2,
                OffsetDateTime.parse("2026-11-05T20:36:46Z"), request);
    }
}
