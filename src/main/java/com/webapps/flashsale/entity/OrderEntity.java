package com.webapps.flashsale.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "orders")
@DynamicInsert
public class OrderEntity {
    @Id @Column(name = "order_id", nullable = false)
    public Long orderId;
    @Column(name = "user_id", nullable = false)
    public Long userId;
    @Column(name = "event_id")
    public Long eventId;
    @Column(name = "event_product_id")
    public Long eventProductId;
    @Column(nullable = false, length = 20)
    public String status;
    @Column(name = "idempotency_key", length = 128)
    public String idempotencyKey;
    @Column(name = "expires_at", nullable = false)
    public LocalDateTime expiresAt;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
