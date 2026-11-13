package com.webapps.flashsale.outbox;

/** Optional, best-effort hint that an outbox record is ready for relay. */
public interface RelayTrigger {
    void enqueue(long eventId, String idempotencyKey);
}
