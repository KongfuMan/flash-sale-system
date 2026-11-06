package com.webapps.flashsale.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "order_payment")
@DynamicInsert
public class OrderPaymentEntity {
    @Id @Column(name = "payment_id", nullable = false)
    public Long paymentId;
    @Column(name = "order_id", nullable = false)
    public Long orderId;
    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal amount;
    @Column(name = "payment_method", length = 30)
    public String paymentMethod;
    @Column(nullable = false, length = 20)
    public String status;
    @Column(name = "transaction_id", length = 128)
    public String transactionId;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "paid_at")
    public LocalDateTime paidAt;
}
