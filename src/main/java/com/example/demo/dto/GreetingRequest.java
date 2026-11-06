package com.example.demo.dto;

import jakarta.validation.constraints.Pattern;

public record GreetingRequest(
    @Pattern(regexp = "(?s).*[^\\p{javaWhitespace}].*", message = "name must not be blank") String name
) {
    public GreetingRequest {
        if (name == null || name.isEmpty()) {
            name = "World";
        }
    }
}
