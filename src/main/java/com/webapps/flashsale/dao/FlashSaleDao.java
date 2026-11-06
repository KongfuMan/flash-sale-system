package com.webapps.flashsale.dao;

import com.webapps.flashsale.entity.FlashSaleTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashSaleDao extends JpaRepository<FlashSaleTemplateEntity, Long> {}
