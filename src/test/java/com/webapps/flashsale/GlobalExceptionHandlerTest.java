package com.webapps.flashsale;

import com.webapps.flashsale.controller.FlashSaleEventOrderController;
import com.webapps.flashsale.exception.GlobalExceptionHandler;
import com.webapps.flashsale.service.OrderService;
import com.webapps.flashsale.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private final OrderService orders = mock(OrderService.class);
    private final ReservationService reservations = mock(ReservationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FlashSaleEventOrderController(orders, reservations))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsValidationAndMalformedRequestErrors() throws Exception {
        mvc.perform(post("/api/v1/flash_sale_event/1/product/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":null,\"idempotencyKey\":\"key\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/v1/flash_sale_event/1/product/2").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request parameters or body"));
        mvc.perform(get("/api/v1/flash_sale_event/not-a-number/orders"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    void preservesExplicitStatusAndReason() throws Exception {
        when(reservations.reserve(anyLong(), anyLong(), anyLong(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Out of stock"));
        mvc.perform(post("/api/v1/flash_sale_event/1/product/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":3,\"idempotencyKey\":\"key\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Out of stock"));
    }

    @Test
    void hidesDatabaseAndUnexpectedFailureDetails() throws Exception {
        when(orders.getOrders(anyInt(), anyInt())).thenThrow(new DataAccessResourceFailureException("private database details"))
                .thenThrow(new IllegalStateException("private internal details"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/v1/flash_sale_event/1/orders"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Internal server error"));
        }
    }

    @Test
    void preservesMethodNotAllowedAndAllowHeader() throws Exception {
        mvc.perform(post("/api/v1/flash_sale_event/1/orders"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.message").value("HTTP method not supported"));
    }
}
