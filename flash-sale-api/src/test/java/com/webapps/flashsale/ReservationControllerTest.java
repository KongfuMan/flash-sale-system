package com.webapps.flashsale;

import com.webapps.flashsale.controller.OrderController;
import com.webapps.flashsale.exception.GlobalExceptionHandler;
import com.webapps.flashsale.service.OrderService;
import com.webapps.flashsale.inventory.InventoryReservationResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationControllerTest {
    private final OrderService reservations = mock(OrderService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new OrderController(reservations))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void reportsIncompleteDeliveryForReservedAndReplay() throws Exception {
        when(reservations.reserve(1, 2, 3, "key"))
                .thenReturn(InventoryReservationResult.RESERVED, InventoryReservationResult.REPLAY);
        var request = post("/api/v1/flash_sale_event/1/product/2")
                .header("UserId", 3).header("Idempotency-Key", "key");
        mvc.perform(request).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Inventory reserved in Redis, but order delivery is not implemented; no order was created"));
        mvc.perform(request).andExpect(status().isServiceUnavailable());
        verify(reservations, times(2)).reserve(1, 2, 3, "key");
    }

    @Test
    void validatesRequest() throws Exception {
        when(reservations.reserve(1, 2, 0, " ")).thenReturn(InventoryReservationResult.INVALID_ARGUMENT);
        mvc.perform(post("/api/v1/flash_sale_event/1/product/2")
                .header("UserId", 0).header("Idempotency-Key", " "))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/flash_sale_event/0/product/2")
                .header("UserId", 3).header("Idempotency-Key", "key"))
                .andExpect(status().isBadRequest());
        verify(reservations).reserve(1, 2, 0, " ");
        verifyNoMoreInteractions(reservations);
    }

    @Test
    void mapsInventoryRejections() throws Exception {
        var request = post("/api/v1/flash_sale_event/1/product/2")
                .header("UserId", 3).header("Idempotency-Key", "key");
        when(reservations.reserve(1, 2, 3, "key")).thenReturn(
                InventoryReservationResult.OUT_OF_STOCK, InventoryReservationResult.CONFLICT,
                InventoryReservationResult.EVENT_NOT_RUNNING, InventoryReservationResult.MISSING_PRODUCT,
                InventoryReservationResult.MISSING_EVENT, InventoryReservationResult.INVALID_STATE);
        mvc.perform(request).andExpect(status().isConflict());
        mvc.perform(request).andExpect(status().isConflict());
        mvc.perform(request).andExpect(status().isConflict());
        mvc.perform(request).andExpect(status().isNotFound());
        mvc.perform(request).andExpect(status().isServiceUnavailable());
        mvc.perform(request).andExpect(status().isServiceUnavailable());
    }
}
