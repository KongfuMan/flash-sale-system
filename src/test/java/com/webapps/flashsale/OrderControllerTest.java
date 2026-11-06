package com.webapps.flashsale;

import com.webapps.flashsale.controller.OrderController;
import com.webapps.flashsale.dto.OrderResponse;
import com.webapps.flashsale.security.SecurityConfiguration;
import com.webapps.flashsale.service.OrderService;
import java.util.List;
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
        when(orders.getAllOrders()).thenReturn(List.of(
                new OrderResponse(1L, 2L, null, null, "PENDING", null, null, null)));
        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].userId").value(2))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].idempotencyKey").doesNotExist());
    }

    @Test
    void returnsEmptyArrayWhenNoOrdersExist() throws Exception {
        when(orders.getAllOrders()).thenReturn(List.of());
        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
