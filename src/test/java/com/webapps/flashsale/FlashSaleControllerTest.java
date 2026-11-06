package com.webapps.flashsale;

import com.webapps.flashsale.controller.FlashSaleController;
import com.webapps.flashsale.service.FlashSaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlashSaleController.class)
@AutoConfigureMockMvc(addFilters = false)
class FlashSaleControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FlashSaleService greetingService;

    @Test
    void greetsWorldByDefault() throws Exception {
        when(greetingService.greet("World")).thenReturn("Hello, World!");
        mvc.perform(get("/api/v1/greeting"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello, World!"));
    }

    @Test
    void greetsTheRequestedName() throws Exception {
        when(greetingService.greet("Charles")).thenReturn("Hello, Charles!");
        mvc.perform(get("/api/v1/greeting").param("name", "Charles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello, Charles!"));
    }
}
