package com.example.demo;

import com.example.demo.controller.GreetingController;
import com.example.demo.service.GreetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GreetingController.class)
@AutoConfigureMockMvc(addFilters = false)
class GreetingValidationTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GreetingService greetingService;

    @Test
    void rejectsWhitespaceOnlyName() throws Exception {
        mvc.perform(get("/api/v1/greeting").param("name", "   "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("name must not be blank"));
        verifyNoInteractions(greetingService);
    }

    @Test
    void rejectsUnicodeWhitespaceOnlyName() throws Exception {
        mvc.perform(get("/api/v1/greeting").param("name", "\u2003"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("name must not be blank"));
        verifyNoInteractions(greetingService);
    }

    @Test
    void preservesWhitespaceAroundValidName() throws Exception {
        when(greetingService.greet(" Alice ")).thenReturn("Hello,  Alice !");
        mvc.perform(get("/api/v1/greeting").param("name", " Alice "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello,  Alice !"));
    }

    @Test
    void emptyNameStillUsesDefault() throws Exception {
        when(greetingService.greet("World")).thenReturn("Hello, World!");
        mvc.perform(get("/api/v1/greeting").param("name", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello, World!"));
    }
}
