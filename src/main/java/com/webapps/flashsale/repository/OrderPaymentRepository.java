package com.webapps.flashsale.repository;

import com.webapps.flashsale.entity.OrderPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentRepository extends JpaRepository<OrderPaymentEntity, Long> {}
