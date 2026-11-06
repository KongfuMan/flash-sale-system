package com.webapps.flashsale;

import com.webapps.flashsale.controller.OrderController;
import com.webapps.flashsale.dto.OrderResponse;
import com.webapps.flashsale.dto.OrderItemResponse;
import com.webapps.flashsale.dto.OrderPageResponse;
import com.webapps.flashsale.security.SecurityConfiguration;
import com.webapps.flashsale.service.OrderService;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfiguration.class)
class OrderControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService orders;

    @Test
    void returnsOrdersWithoutAuthentication() throws Exception {
        when(orders.getOrders(0, 20)).thenReturn(new OrderPageResponse(List.of(
                new OrderResponse(1L, 2L, null, null, "PENDING", null, null, null,
                        List.of(new OrderItemResponse(5L, 6L, null, 2, new BigDecimal("19.99"))))), 0, 20, true));
        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].orderId").value(1))
                .andExpect(jsonPath("$.orders[0].userId").value(2))
                .andExpect(jsonPath("$.orders[0].status").value("PENDING"))
                .andExpect(jsonPath("$.orders[0].items[0].unitPriceSnapshot").value(19.99))
                .andExpect(jsonPath("$.orders[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void returnsEmptyArrayWhenNoOrdersExist() throws Exception {
        when(orders.getOrders(1, 5)).thenReturn(new OrderPageResponse(List.of(), 1, 5, false));
        mvc.perform(get("/api/v1/orders").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(0))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mvc.perform(get("/api/v1/orders").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders").param("size", "0")).andExpect(status().isBadRequest());
    }
}
