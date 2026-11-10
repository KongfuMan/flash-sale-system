package com.webapps.flashsale;

import com.webapps.flashsale.controller.OrderController;
import com.webapps.flashsale.exception.GlobalExceptionHandler;
import com.webapps.flashsale.service.OrderService;
import com.webapps.flashsale.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationControllerTest {
    private final ReservationService reservations = mock(ReservationService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new OrderController(mock(OrderService.class), reservations))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void returnsCreatedAndReplay() throws Exception {
        when(reservations.reserve(1, 2, 3, "key"))
                .thenReturn(new ReservationService.Result(42, true), new ReservationService.Result(42, false));
        var request = post("/api/v1/flash_sale_event/1/product/2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":3,\"idempotencyKey\":\"key\"}");
        mvc.perform(request).andExpect(status().isCreated()).andExpect(jsonPath("$.orderId").value(42));
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(42));
        verify(reservations, times(2)).reserve(1, 2, 3, "key");
    }

    @Test
    void validatesRequest() throws Exception {
        mvc.perform(post("/api/v1/flash_sale_event/1/product/2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":0,\"idempotencyKey\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/flash_sale_event/0/product/2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":3,\"idempotencyKey\":\"key\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservations);
    }
}
