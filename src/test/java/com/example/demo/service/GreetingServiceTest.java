package com.example.demo.service;

import com.example.demo.repository.GreetingRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GreetingServiceTest {
    @Test
    void formatsGreeting() {
        GreetingRepository repository = mock(GreetingRepository.class);
        when(repository.getTemplate()).thenReturn("Hello, %s!");
        assertThat(new GreetingService(repository).greet("Charles")).isEqualTo("Hello, Charles!");
    }
}
