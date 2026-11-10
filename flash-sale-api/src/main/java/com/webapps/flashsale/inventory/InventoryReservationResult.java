package com.webapps.flashsale.inventory;

public enum InventoryReservationResult {
    RESERVED,
    REPLAY,
    CONFLICT,
    OUT_OF_STOCK,
    MISSING_EVENT,
    INVALID_STATE,
    EVENT_NOT_RUNNING,
    MISSING_PRODUCT,
    INVALID_ARGUMENT
}
