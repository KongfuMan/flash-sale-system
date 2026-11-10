package com.webapps.flashsale;

import com.webapps.flashsale.inventory.InventoryPrimitives;
import com.webapps.flashsale.inventory.InventoryReservationResult;
import com.webapps.flashsale.repository.OrderItemRepository;
import com.webapps.flashsale.repository.OrderRepository;
import com.webapps.flashsale.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionReservationServiceTest {
    private final InventoryPrimitives inventory = mock(InventoryPrimitives.class);
    private final OrderService service = new OrderService(mock(OrderRepository.class), mock(OrderItemRepository.class), inventory);

    @Test
    void delegatesReservationToRedis() {
        when(inventory.reserve(1, 2, 3, "key")).thenReturn(InventoryReservationResult.RESERVED);
        assertEquals(InventoryReservationResult.RESERVED, service.reserve(1, 2, 3, "key"));
        verify(inventory).reserve(1, 2, 3, "key");
    }

    @Test
    void redisFailureDoesNotReportSuccess() {
        when(inventory.reserve(1, 2, 3, "key")).thenThrow(new DataAccessResourceFailureException("unavailable"));
        var error = assertThrows(ResponseStatusException.class, () -> service.reserve(1, 2, 3, "key"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }
}
