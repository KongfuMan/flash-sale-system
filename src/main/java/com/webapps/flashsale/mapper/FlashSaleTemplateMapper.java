package com.webapps.flashsale.mapper;

import com.webapps.flashsale.domain.FlashSaleTemplate;
import com.webapps.flashsale.entity.FlashSaleTemplateEntity;

public final class FlashSaleTemplateMapper {
    private FlashSaleTemplateMapper() {}

    public static FlashSaleTemplate toDomain(FlashSaleTemplateEntity entity) {
        return new FlashSaleTemplate(entity.getTemplate());
    }
}
