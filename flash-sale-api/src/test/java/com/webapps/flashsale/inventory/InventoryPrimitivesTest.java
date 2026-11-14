package com.webapps.flashsale.inventory;

import com.webapps.flashsale.entity.FlashSaleEventEntity;
import com.webapps.flashsale.entity.FlashSaleEventProductEntity;
import com.webapps.flashsale.repository.FlashSaleEventProductRepository;
import com.webapps.flashsale.repository.FlashSaleEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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

    @Test void refusesPrefillWithoutProducts() {
        var event = new FlashSaleEventEntity();
        event.status = "RUNNING";
        when(events.findById(1L)).thenReturn(Optional.of(event));
        when(products.findByEventId(1L)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> inventory.prefill(1));
        verifyNoInteractions(redis);
    }

    @Test void prefillsProductStockUsingProdPrefix() {
        var event = new FlashSaleEventEntity();
        event.status = "RUNNING";
        event.endAt = LocalDateTime.of(2026, 1, 1, 1, 0);
        var product = new FlashSaleEventProductEntity();
        product.eventProductId = 2L;
        product.allocatedQuantity = 10;
        product.soldQuantity = 2;
        product.reservedQuantity = 1;
        when(events.findById(1L)).thenReturn(Optional.of(event));
        when(products.findByEventId(1L)).thenReturn(List.of(product));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        var fixedInventory = new InventoryPrimitives(events, products, redis, Duration.ofHours(1),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertTrue(fixedInventory.prefill(1));
        verify(redis).execute(any(RedisScript.class), eq(List.of("flashsale:inventory:v1:{event:1}")),
                eq("7200"), eq("status"), eq("RUNNING"), eq("prod:2"), eq("7"));
    }

    @Test void rejectsExpiredEventRetention() {
        var event = new FlashSaleEventEntity();
        event.status = "ENDED";
        event.endAt = LocalDateTime.of(2025, 12, 31, 22, 0);
        when(events.findById(1L)).thenReturn(Optional.of(event));
        when(products.findByEventId(1L)).thenReturn(List.of(new FlashSaleEventProductEntity()));
        var fixedInventory = new InventoryPrimitives(events, products, redis, Duration.ofHours(1),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class, () -> fixedInventory.prefill(1));
        verifyNoInteractions(redis);
    }

    @Test void mapsReserveFailuresAndRejectsInvalidIds() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(
                "MISSING_EVENT", "INVALID_STATE", "EVENT_NOT_RUNNING", "MISSING_PRODUCT",
                "OUT_OF_STOCK", "RESERVED", "REPLAY", "LEGACY_REPLAY", "CONFLICT");
        for (var expected : new InventoryReservationResult[] {InventoryReservationResult.MISSING_EVENT,
                InventoryReservationResult.INVALID_STATE, InventoryReservationResult.EVENT_NOT_RUNNING,
                InventoryReservationResult.MISSING_PRODUCT, InventoryReservationResult.OUT_OF_STOCK,
                InventoryReservationResult.RESERVED, InventoryReservationResult.REPLAY,
                InventoryReservationResult.LEGACY_REPLAY, InventoryReservationResult.CONFLICT}) {
            assertEquals(expected, inventory.reserve(1, 2, 3, "key", "payload"));
        }
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(0, 2, 3, "key", "payload"));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 0, 3, "key", "payload"));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 2, 0, "key", "payload"));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 2, 3, null, "payload"));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 2, 3, "  ", "payload"));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 2, 3, "x".repeat(129), "payload"));
        assertEquals(InventoryReservationResult.INVALID_ARGUMENT, inventory.reserve(1, 2, 3, "key", ""));
    }

    @Test void rejectsUnknownLuaResult() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn("UNKNOWN");
        assertThrows(IllegalStateException.class, () -> inventory.reserve(1, 2, 3, "key", "payload"));
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
