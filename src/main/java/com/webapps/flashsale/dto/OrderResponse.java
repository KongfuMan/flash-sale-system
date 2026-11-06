package com.webapps.flashsale.dto;

import java.time.LocalDateTime;

public record OrderResponse(Long orderId, Long userId, Long eventId, Long eventProductId,
                            String status, LocalDateTime expiresAt, LocalDateTime createdAt,
                            LocalDateTime updatedAt) {}
