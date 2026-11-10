package com.webapps.flashsale.dto;

import java.util.List;

public record OrderPageResponse(
        List<OrderResponse> orders,
        int page,
        int size,
        boolean hasNext) {}
