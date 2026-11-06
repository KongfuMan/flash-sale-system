package com.webapps.flashsale.service;

import com.webapps.flashsale.repository.FlashSaleRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlashSaleServiceTest {
    @Test
    void formatsGreeting() {
        FlashSaleRepository repository = mock(FlashSaleRepository.class);
        when(repository.getTemplate()).thenReturn("Hello, %s!");
        assertThat(new FlashSaleService(repository).greet("Charles")).isEqualTo("Hello, Charles!");
    }
}
