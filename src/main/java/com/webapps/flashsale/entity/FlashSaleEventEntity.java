package com.webapps.flashsale.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "flash_sale_events")
@DynamicInsert
public class FlashSaleEventEntity {
    @Id @Column(name = "event_id", nullable = false)
    public Long eventId;
    @Column(nullable = false, length = 255)
    public String name;
    @Column(name = "start_at", nullable = false)
    public LocalDateTime startAt;
    @Column(name = "end_at", nullable = false)
    public LocalDateTime endAt;
    @Column(nullable = false, length = 20)
    public String status;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
