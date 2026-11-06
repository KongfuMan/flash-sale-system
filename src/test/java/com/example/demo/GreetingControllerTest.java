package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GreetingController.class)
class GreetingControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void greetsWorldByDefault() throws Exception {
        mvc.perform(get("/api/greeting"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello, World!"));
    }

    @Test
    void greetsTheRequestedName() throws Exception {
        mvc.perform(get("/api/greeting").param("name", "Charles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello, Charles!"));
    }
}
