package com.webapps.flashsale;

import com.webapps.flashsale.service.FlashSaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FlashSalePersistenceTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FlashSaleService service;

    @Test
    void greetingUsesStoredTemplate() {
        jdbc.update("UPDATE greeting_template SET template = ? WHERE id = 1", "Welcome, %s!");
        assertThat(service.greet("Charles")).isEqualTo("Welcome, Charles!");
    }
}
