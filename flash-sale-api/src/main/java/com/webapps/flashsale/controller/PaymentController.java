package com.webapps.flashsale.controller;

import com.webapps.flashsale.dto.ErrorResponse;
import com.webapps.flashsale.dto.PaymentRequest;
import com.webapps.flashsale.dto.PaymentResponse;
import com.webapps.flashsale.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /**
     * Records a payment for an eligible order.
     *
     * @param orderId order to pay
     * @param request payment identifier, transaction identifier, and amount
     * @return 201 with the recorded payment
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> pay(@PathVariable long orderId, @Valid @RequestBody PaymentRequest request) {
        if (orderId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId must be positive");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(payments.pay(orderId, request));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> duplicatePayment(DuplicateKeyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("Payment ID or transaction ID already used"));
    }
}
