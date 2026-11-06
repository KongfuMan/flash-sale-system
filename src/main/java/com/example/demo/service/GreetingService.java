package com.example.demo.service;

import com.example.demo.repository.GreetingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    private static final Logger log = LoggerFactory.getLogger(GreetingService.class);
    private final GreetingRepository greetingRepository;

    public GreetingService(GreetingRepository greetingRepository) {
        this.greetingRepository = greetingRepository;
    }

    public String greet(String name) {
        log.debug("Generating greeting");
        return greetingRepository.getTemplate().formatted(name);
    }
}
