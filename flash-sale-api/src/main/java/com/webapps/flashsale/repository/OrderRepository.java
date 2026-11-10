package com.webapps.flashsale.repository;

import com.webapps.flashsale.entity.OrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Slice<OrderEntity> findAllBy(Pageable pageable);
}
