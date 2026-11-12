package com.webapps.flashsale.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryMarkerTest {
    @Test
    void serializesPendingMarkerWithStringIds() throws Exception {
        var mapper = new ObjectMapper();
        var marker = InventoryMarker.pending("key\"\\", "request", Long.MAX_VALUE, 2, 3);
        assertEquals(mapper.readTree("""
                {"version":3,"status":"PENDING","idempotencyKey":"key\\\"\\\\","requestId":"request",
                 "eventId":"9223372036854775807","eventProductId":"2","userId":"3"}
                """), mapper.readTree(mapper.writeValueAsString(marker)));
    }
}
