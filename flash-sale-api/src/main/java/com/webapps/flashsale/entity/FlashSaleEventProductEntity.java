package com.webapps.flashsale.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "flash_sale_event_products")
@DynamicInsert
public class FlashSaleEventProductEntity {
    @Id @Column(name = "event_product_id", nullable = false)
    public Long eventProductId;
    @Column(name = "event_id", nullable = false)
    public Long eventId;
    @Column(name = "product_id", nullable = false)
    public Long productId;
    @Column(name = "allocated_quantity", nullable = false)
    public Integer allocatedQuantity;
    @Column(name = "sold_quantity", nullable = false)
    public Integer soldQuantity;
    @Column(name = "flash_price", nullable = false, precision = 10, scale = 2)
    public BigDecimal flashPrice;
    @Column(name = "per_user_limit", nullable = false)
    public Integer perUserLimit;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    @Column(name = "reserved_quantity", nullable = false)
    public Integer reservedQuantity;
}
