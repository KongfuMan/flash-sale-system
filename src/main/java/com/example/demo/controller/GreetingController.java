package com.example.demo.controller;

import com.example.demo.service.GreetingService;
import com.example.demo.dto.GreetingRequest;
import com.example.demo.dto.GreetingResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GreetingController {
    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/greeting")
    public GreetingResponse greeting(@Valid @ModelAttribute GreetingRequest request) {
        return new GreetingResponse(greetingService.greet(request.name()));
    }
}
