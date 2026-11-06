package com.example.demo.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectProvider<UserDetailsService> users;

    @Test
    void permitsGreetingAndHealthReads() throws Exception {
        mvc.perform(get("/api/v1/greeting")).andExpect(status().isOk());
        mvc.perform(head("/api/v1/greeting")).andExpect(status().isOk());
        // Actuator is optional; its absence produces 404 rather than 200.
        assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus()).isIn(200, 404);
        assertThat(mvc.perform(head("/actuator/health")).andReturn().getResponse().getStatus()).isIn(200, 404);
        assertThat(users.getIfAvailable()).isNull();
    }

    @Test
    void deniesOtherRoutesAndMethodsEvenWithCsrfToken() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health/details")).andExpect(status().isForbidden());
        mvc.perform(get("/other")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/greeting")).andExpect(status().isForbidden());

        var result = mvc.perform(get("/api/v1/greeting")).andReturn();
        var token = (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
        assertThat(token).isNotNull();
        mvc.perform(post("/api/v1/greeting")
                .session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession())
                .param(token.getParameterName(), token.getToken()))
                .andExpect(status().isForbidden());
    }
}
