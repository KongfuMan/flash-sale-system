package com.webapps.flashsale.controller;

import com.webapps.flashsale.service.FlashSaleService;
import com.webapps.flashsale.dto.FlashSaleRequest;
import com.webapps.flashsale.dto.FlashSaleResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FlashSaleController {
    private final FlashSaleService greetingService;

    public FlashSaleController(FlashSaleService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/greeting")
    public FlashSaleResponse greeting(@Valid @ModelAttribute FlashSaleRequest request) {
        return new FlashSaleResponse(greetingService.greet(request.name()));
    }
}
