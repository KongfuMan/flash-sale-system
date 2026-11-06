package com.webapps.flashsale.repository;

import com.webapps.flashsale.entity.OrderItemEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    List<OrderItemEntity> findByOrderIdInOrderByOrderItemIdAsc(Collection<Long> orderIds);
}
