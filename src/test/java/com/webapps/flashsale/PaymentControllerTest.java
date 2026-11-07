package com.webapps.flashsale;

import com.webapps.flashsale.controller.PaymentController;
import com.webapps.flashsale.dto.PaymentRequest;
import com.webapps.flashsale.dto.PaymentResponse;
import com.webapps.flashsale.exception.GlobalExceptionHandler;
import com.webapps.flashsale.service.PaymentService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerTest {
    private final PaymentService service = mock(PaymentService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void createsPayment() throws Exception {
        when(service.pay(5, new PaymentRequest(10L, "txn", null)))
                .thenReturn(new PaymentResponse(10, 5, "txn", new BigDecimal("12.34"), "SUCCESS"));
        mvc.perform(post("/api/v1/orders/5/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":10,\"transactionId\":\"txn\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(12.34))
                .andExpect(jsonPath("$.paymentId").value(10)).andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void validatesInputs() throws Exception {
        for (String body : new String[]{"{}", "{\"paymentId\":0,\"transactionId\":\"x\"}",
                "{\"paymentId\":1,\"transactionId\":\" \"}", "{\"paymentId\":9223372036854775808,\"transactionId\":\"x\"}"}) {
            mvc.perform(post("/api/v1/orders/5/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/orders/0/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":1,\"transactionId\":\"x\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void hidesDuplicateDatabaseDetails() throws Exception {
        when(service.pay(anyLong(), any())).thenThrow(new DuplicateKeyException("secret database details"));
        mvc.perform(post("/api/v1/orders/5/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":10,\"transactionId\":\"txn\"}"))
                .andExpect(status().isConflict()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
