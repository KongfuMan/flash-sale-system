package com.webapps.flashsale.dto;

import jakarta.validation.constraints.Pattern;

public record FlashSaleRequest(
    @Pattern(regexp = "(?s).*[^\\p{javaWhitespace}].*", message = "name must not be blank") String name
) {
    public FlashSaleRequest {
        if (name == null || name.isEmpty()) {
            name = "World";
        }
    }
}
