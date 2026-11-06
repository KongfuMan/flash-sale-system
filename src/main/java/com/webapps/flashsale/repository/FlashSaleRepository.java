package com.webapps.flashsale.repository;

import com.webapps.flashsale.dao.FlashSaleDao;
import com.webapps.flashsale.mapper.FlashSaleTemplateMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FlashSaleRepository {
    private final FlashSaleDao greetingDao;

    public FlashSaleRepository(FlashSaleDao greetingDao) {
        this.greetingDao = greetingDao;
    }

    public String getTemplate() {
        return FlashSaleTemplateMapper.toDomain(greetingDao.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default greeting template is missing"))).template();
    }
}
