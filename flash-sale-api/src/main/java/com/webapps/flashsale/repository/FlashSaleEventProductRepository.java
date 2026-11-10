package com.webapps.flashsale.repository;

import com.webapps.flashsale.entity.FlashSaleEventProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface FlashSaleEventProductRepository extends JpaRepository<FlashSaleEventProductEntity, Long> {
    List<FlashSaleEventProductEntity> findByEventId(Long eventId, Pageable pageable);
}
