package com.webapps.flashsale.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long orderItemId,
        Long productId,
        Long eventProductId,
        Integer quantity,
        BigDecimal unitPriceSnapshot) {}
