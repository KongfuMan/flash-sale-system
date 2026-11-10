package com.webapps.flashsale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
        @NotNull @Positive Long paymentId,
        @NotBlank @Size(max = 128) String transactionId,
        @Size(max = 30) String paymentMethod) {}
