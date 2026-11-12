package com.webapps.flashsale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webapps.flashsale.inventory.InventoryPrimitives;
import com.webapps.flashsale.inventory.InventoryReservationResult;
import com.webapps.flashsale.repository.OrderItemRepository;
import com.webapps.flashsale.repository.OrderRepository;
import com.webapps.flashsale.service.OrderService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionReservationServiceTest {
    private final InventoryPrimitives inventory = mock(InventoryPrimitives.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OrderService service = new OrderService(mock(OrderRepository.class), mock(OrderItemRepository.class),
            inventory, mapper);

    @Test void passesFullConsumerEnvelopeToInventory() throws Exception {
        when(inventory.reserve(eq(1L), eq(2L), eq(3L), eq("key"), anyString())).thenReturn(InventoryReservationResult.RESERVED);
        assertEquals(InventoryReservationResult.RESERVED, service.reserve(1, 2, 3, "key"));
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(inventory).reserve(eq(1L), eq(2L), eq(3L), eq("key"), json.capture());
        var message = mapper.readTree(json.getValue());
        assertEquals(UUID.nameUUIDFromBytes("1:key".getBytes(StandardCharsets.UTF_8)).toString(), message.path("message_id").asText());
        assertEquals("ORDER_REQUESTED", message.path("message_type").asText());
        assertEquals(2, message.path("schema_version").asInt());
        assertEquals(1, message.path("data").path("flash_sale_event_id").asInt());
        assertEquals(3, message.path("data").path("user_id").asInt());
        assertEquals(2, message.path("data").path("items").get(0).path("event_product_id").asInt());
        assertEquals(1, message.path("data").path("items").get(0).path("quantity").asInt());
    }

    @Test void redisFailureDoesNotReportSuccess() {
        when(inventory.reserve(eq(1L), eq(2L), eq(3L), eq("key"), anyString()))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        var error = assertThrows(ResponseStatusException.class, () -> service.reserve(1, 2, 3, "key"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }
}
