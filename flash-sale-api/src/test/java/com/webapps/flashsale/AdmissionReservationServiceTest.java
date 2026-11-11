package com.webapps.flashsale;

import com.webapps.flashsale.inventory.InventoryPrimitives;
import com.webapps.flashsale.inventory.InventoryReservationResult;
import com.webapps.flashsale.repository.OrderItemRepository;
import com.webapps.flashsale.repository.OrderRepository;
import com.webapps.flashsale.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionReservationServiceTest {
    private final InventoryPrimitives inventory = mock(InventoryPrimitives.class);
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OrderService service = new OrderService(mock(OrderRepository.class), mock(OrderItemRepository.class),
            inventory, kafka, mapper, "order-requests-v1");

    @Test
    void publishesReservedRequestInConsumerFormat() throws Exception {
        when(inventory.reserve(1, 2, 3, "key")).thenReturn(InventoryReservationResult.RESERVED);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        assertEquals(InventoryReservationResult.RESERVED, service.reserve(1, 2, 3, "key"));
        verify(inventory).reserve(1, 2, 3, "key");
        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("order-requests-v1"), key.capture(), json.capture());
        var message = mapper.readTree(json.getValue());
        assertEquals(key.getValue(), message.path("message_id").asText());
        assertEquals("ORDER_REQUESTED", message.path("message_type").asText());
        assertEquals(2, message.path("schema_version").asInt());
        assertEquals(1, message.path("data").path("flash_sale_event_id").asInt());
        assertEquals(3, message.path("data").path("user_id").asInt());
        assertEquals(2, message.path("data").path("items").get(0).path("event_product_id").asInt());
        assertEquals(1, message.path("data").path("items").get(0).path("quantity").asInt());
    }

    @Test
    void redisFailureDoesNotReportSuccess() {
        when(inventory.reserve(1, 2, 3, "key")).thenThrow(new DataAccessResourceFailureException("unavailable"));
        var error = assertThrows(ResponseStatusException.class, () -> service.reserve(1, 2, 3, "key"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verifyNoInteractions(kafka);
    }

    @Test
    void publishesOnlyNewReservations() {
        for (var result : InventoryReservationResult.values()) {
            if (result == InventoryReservationResult.RESERVED) continue;
            when(inventory.reserve(1, 2, 3, "key")).thenReturn(result);
            assertEquals(result, service.reserve(1, 2, 3, "key"));
        }
        verifyNoInteractions(kafka);
    }

    @Test
    void kafkaFailureDoesNotReportSuccess() {
        when(inventory.reserve(1, 2, 3, "key")).thenReturn(InventoryReservationResult.RESERVED);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unavailable")));
        var error = assertThrows(ResponseStatusException.class, () -> service.reserve(1, 2, 3, "key"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void timeoutIsUncertainAndDoesNotRetryOrRestoreStock() throws Exception {
        when(inventory.reserve(1, 2, 3, "key")).thenReturn(InventoryReservationResult.RESERVED);
        @SuppressWarnings("unchecked") CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(future.get(5, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
        var error = assertThrows(ResponseStatusException.class, () -> service.reserve(1, 2, 3, "key"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verify(inventory, times(1)).reserve(1, 2, 3, "key");
        verifyNoMoreInteractions(inventory);
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
    }
}
