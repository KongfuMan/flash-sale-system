package com.webapps.flashsale.inventory;

import com.webapps.flashsale.entity.FlashSaleEventEntity;
import com.webapps.flashsale.entity.FlashSaleEventProductEntity;
import com.webapps.flashsale.repository.FlashSaleEventProductRepository;
import com.webapps.flashsale.repository.FlashSaleEventRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryPrimitivesTest {
    private final FlashSaleEventRepository events = mock(FlashSaleEventRepository.class);
    private final FlashSaleEventProductRepository products = mock(FlashSaleEventProductRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final InventoryPrimitives inventory = new InventoryPrimitives(events, products, redis);

    @Test void refusesIncompletePrefill() {
        var event = new FlashSaleEventEntity();
        event.status = "RUNNING";
        when(events.findById(1L)).thenReturn(Optional.of(event));
        when(products.findByEventId(eq(1L), any(Pageable.class)))
                .thenReturn(java.util.Collections.nCopies(1001, new FlashSaleEventProductEntity()));
        assertThrows(IllegalArgumentException.class, () -> inventory.prefill(1));
        verifyNoInteractions(redis);
    }

    @Test void mapsReserveFailuresAndRejectsInvalidIds() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(
                "MISSING_EVENT", "INVALID_STATE", "EVENT_NOT_RUNNING", "MISSING_PRODUCT",
                "OUT_OF_STOCK", "RESERVED", "REPLAY", "CONFLICT");
        for (var expected : new InventoryReservationResult[] {InventoryReservationResult.MISSING_EVENT,
                InventoryReservationResult.INVALID_STATE, InventoryReservationResult.EVENT_NOT_RUNNING,
                InventoryReservationResult.MISSING_PRODUCT, InventoryReservationResult.OUT_OF_STOCK,
                InventoryReservationResult.RESERVED, InventoryReservationResult.REPLAY, InventoryReservationResult.CONFLICT}) {
            assertEquals(expected, inventory.reserve(1, 2, 3, "key"));
        }
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(0, 2, 3, "key"));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 0, 3, "key"));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 2, 0, "key"));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 2, 3, null));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 2, 3, "  "));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 2, 3, "x".repeat(129)));
    }

    @Test void rejectsUnknownLuaResult() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn("UNKNOWN");
        assertThrows(IllegalStateException.class, () -> inventory.reserve(1, 2, 3, "key"));
    }

    @Test void activationRequiresRunningDatabaseEventBeforeRedis() {
        assertThrows(IllegalArgumentException.class, () -> inventory.activate(0));
        assertThrows(IllegalArgumentException.class, () -> inventory.activate(1));
        var event = new FlashSaleEventEntity();
        when(events.findById(1L)).thenReturn(Optional.of(event));
        for (String status : new String[] {null, "UPCOMING", "ENDED", "CANCELED", "invalid"}) {
            event.status = status;
            assertThrows(IllegalStateException.class, () -> inventory.activate(1));
        }
        verifyNoInteractions(redis, products);
    }

    @Test void activationMapsRedisResultsAndFailsOnUnknownResult() {
        var event = new FlashSaleEventEntity();
        event.status = "RUNNING";
        when(events.findById(1L)).thenReturn(Optional.of(event));
        when(redis.execute(any(RedisScript.class), eq(List.of("flashsale:inventory:v1:{event:1}"))))
                .thenReturn(1L, -1L, -2L, null, 42L);
        assertEquals(InventoryPrimitives.ActivationResult.ACTIVATED, inventory.activate(1));
        assertEquals(InventoryPrimitives.ActivationResult.MISSING_EVENT, inventory.activate(1));
        assertEquals(InventoryPrimitives.ActivationResult.INVALID_STATE, inventory.activate(1));
        assertThrows(IllegalStateException.class, () -> inventory.activate(1));
        assertThrows(IllegalStateException.class, () -> inventory.activate(1));
        verifyNoInteractions(products);
    }
}
