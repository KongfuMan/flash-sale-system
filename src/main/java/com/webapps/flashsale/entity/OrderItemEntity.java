package com.webapps.flashsale.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "order_items")
@DynamicInsert
public class OrderItemEntity {
    @Id @Column(name = "order_item_id", nullable = false)
    public Long orderItemId;
    @Column(name = "order_id", nullable = false)
    public Long orderId;
    @Column(name = "product_id", nullable = false)
    public Long productId;
    @Column(name = "event_product_id")
    public Long eventProductId;
    @Column(nullable = false)
    public Integer quantity;
    @Column(name = "unit_price_snapshot", nullable = false, precision = 10, scale = 2)
    public BigDecimal unitPriceSnapshot;
}
