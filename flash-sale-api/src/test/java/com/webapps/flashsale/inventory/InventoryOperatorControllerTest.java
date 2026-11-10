package com.webapps.flashsale.inventory;

import com.webapps.flashsale.controller.OperationController;
import com.webapps.flashsale.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationController.class)
@Import(SecurityConfiguration.class)
class InventoryOperatorControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private InventoryPrimitives inventory;

    @Test
    void initializesInventoryAnonymously() throws Exception {
        when(inventory.prefill(1)).thenReturn(true);
        mvc.perform(post("/api/v1/operator/events/1/inventory/prefill")).andExpect(status().isCreated());
    }

    @Test
    void rejectsExistingInventory() throws Exception {
        when(inventory.prefill(1)).thenReturn(false);
        mvc.perform(post("/api/v1/operator/events/1/inventory/prefill")).andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidEvent() throws Exception {
        when(inventory.prefill(0)).thenThrow(new IllegalArgumentException("Invalid event ID"));
        mvc.perform(post("/api/v1/operator/events/0/inventory/prefill")).andExpect(status().isBadRequest());
    }
}
