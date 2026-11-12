package com.webapps.flashsale.inventory;

record InventoryMarker(int version, String status, String idempotencyKey, String requestId,
                       String eventId, String eventProductId, String userId) {
    static InventoryMarker pending(String idempotencyKey, String requestId, long eventId, long productId, long userId) {
        return new InventoryMarker(3, "PENDING", idempotencyKey, requestId,
                Long.toString(eventId), Long.toString(productId), Long.toString(userId));
    }
}
