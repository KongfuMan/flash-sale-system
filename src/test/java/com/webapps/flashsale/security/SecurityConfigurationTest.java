package com.webapps.flashsale.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
    void permitsHealthReads() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(head("/actuator/health")).andExpect(status().isOk());
        assertThat(users.getIfAvailable()).isNull();
    }

    @Test
    void allowsAnonymousRequestsWithoutCsrfToken() throws Exception {
        mvc.perform(get("/other")).andExpect(status().isNotFound());
        mvc.perform(post("/other")).andExpect(status().isNotFound());
    }
}
