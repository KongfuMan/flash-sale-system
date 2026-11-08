package com.webapps.flashsale.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "products")
@DynamicInsert
public class ProductEntity {
    @Id @Column(name = "product_id", nullable = false)
    public Long productId;
    @Column(nullable = false, length = 255)
    public String name;
    @Column(name = "total_inventory", nullable = false)
    public Integer totalInventory;
    @Column(name = "unit_price", nullable = false)
    public Float unitPrice;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
