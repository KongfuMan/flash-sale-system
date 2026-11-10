package com.webapps.flashsale.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderCreated(
        Long orderId,
        Long flashSaleEventId,
        Long eventProductId,
        Long userId) implements MessagePayload {}
