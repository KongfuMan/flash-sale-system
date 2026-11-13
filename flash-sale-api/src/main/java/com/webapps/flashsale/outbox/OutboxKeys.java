package com.webapps.flashsale.outbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class OutboxKeys {
    private OutboxKeys() {
    }

    public static String eventKey(long eventId) {
        if (eventId <= 0) throw new IllegalArgumentException("Invalid event ID");
        return "flashsale:inventory:v1:{event:" + eventId + "}";
    }

    public static String requestId(long eventId, String idempotencyKey) {
        return sha256(eventId + ":" + idempotencyKey);
    }

    public static String outboxField(String requestId) {
        return "outbox:" + requestId;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
