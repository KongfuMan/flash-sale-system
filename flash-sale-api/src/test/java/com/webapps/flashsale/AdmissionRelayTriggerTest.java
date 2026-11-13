package com.webapps.flashsale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webapps.flashsale.inventory.InventoryPrimitives;
import com.webapps.flashsale.inventory.InventoryReservationResult;
import com.webapps.flashsale.outbox.RelayTrigger;
import com.webapps.flashsale.repository.OrderItemRepository;
import com.webapps.flashsale.repository.OrderRepository;
import com.webapps.flashsale.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdmissionRelayTriggerTest {
    private final InventoryPrimitives inventory = mock(InventoryPrimitives.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RelayTrigger> provider = mock(ObjectProvider.class);
    private final RelayTrigger trigger = mock(RelayTrigger.class);
    private final OrderService service = new OrderService(mock(OrderRepository.class), mock(OrderItemRepository.class),
            inventory, mapper, provider);

    @BeforeEach
    void setUp() {
        doReturn(trigger).when(provider).getIfAvailable();
    }

    @Test
    void reservedAdmissionEnqueuesBestEffortTrigger() {
        when(inventory.reserve(eq(1L), eq(2L), eq(3L), eq("key"), anyString()))
                .thenReturn(InventoryReservationResult.RESERVED);

        assertEquals(InventoryReservationResult.RESERVED, service.reserve(1, 2, 3, "key"));

        verify(trigger).enqueue(1L, "key");
    }

    @Test
    void replayAdmissionEnqueuesBestEffortTrigger() {
        when(inventory.reserve(eq(1L), eq(2L), eq(3L), eq("key"), anyString()))
                .thenReturn(InventoryReservationResult.REPLAY);

        assertEquals(InventoryReservationResult.REPLAY, service.reserve(1, 2, 3, "key"));

        verify(trigger).enqueue(1L, "key");
    }

    @Test
    void rejectedAdmissionDoesNotEnqueue() {
        for (InventoryReservationResult result : new InventoryReservationResult[]{
                InventoryReservationResult.OUT_OF_STOCK, InventoryReservationResult.CONFLICT,
                InventoryReservationResult.INVALID_ARGUMENT, InventoryReservationResult.EVENT_NOT_RUNNING,
                InventoryReservationResult.MISSING_EVENT, InventoryReservationResult.MISSING_PRODUCT,
                InventoryReservationResult.INVALID_STATE, InventoryReservationResult.LEGACY_REPLAY}) {
            when(inventory.reserve(eq(1L), eq(2L), eq(3L), eq("key"), anyString())).thenReturn(result);

            assertEquals(result, service.reserve(1, 2, 3, "key"));
        }
        verifyNoInteractions(trigger);
    }

    @Test
    void triggerFailureNeverChangesAdmissionResult() {
        when(inventory.reserve(eq(1L), eq(2L), eq(3L), eq("key"), anyString()))
                .thenReturn(InventoryReservationResult.RESERVED);
        doThrow(new RuntimeException("queue unavailable")).when(trigger).enqueue(1L, "key");

        assertDoesNotThrow(() -> assertEquals(InventoryReservationResult.RESERVED, service.reserve(1, 2, 3, "key")));
        verify(trigger).enqueue(1L, "key");
    }

    @Test
    void missingTriggerBeanNeverChangesAdmissionResult() {
        when(inventory.reserve(eq(1L), eq(2L), eq(3L), eq("key"), anyString()))
                .thenReturn(InventoryReservationResult.RESERVED);
        doReturn(null).when(provider).getIfAvailable();

        assertEquals(InventoryReservationResult.RESERVED, service.reserve(1, 2, 3, "key"));
    }
}
