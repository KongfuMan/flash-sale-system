package com.webapps.flashsale.dto;

import java.math.BigDecimal;

public record PaymentResponse(long paymentId, long orderId, String transactionId, BigDecimal amount, String status) {}
