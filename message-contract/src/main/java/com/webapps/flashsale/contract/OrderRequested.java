package com.webapps.flashsale.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderRequested(
        Long flashSaleEventId,
        Long userId,
        List<Item> items) implements MessagePayload {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(
            Long eventProductId,
            Integer quantity) {}
}
