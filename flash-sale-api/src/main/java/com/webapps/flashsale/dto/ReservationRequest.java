package com.webapps.flashsale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReservationRequest(
        @NotNull @Positive Long userId,
        @NotBlank @Size(max = 128) String idempotencyKey) {}
