package com.webapps.flashsale.service;

import com.webapps.flashsale.repository.FlashSaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FlashSaleService {
    private static final Logger log = LoggerFactory.getLogger(FlashSaleService.class);
    private final FlashSaleRepository greetingRepository;

    public FlashSaleService(FlashSaleRepository greetingRepository) {
        this.greetingRepository = greetingRepository;
    }

    public String greet(String name) {
        log.debug("Generating greeting");
        return greetingRepository.getTemplate().formatted(name);
    }
}
