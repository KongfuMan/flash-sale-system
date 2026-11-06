package com.webapps.flashsale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MigrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsGreetingTemplateTable() {
        assertThat(jdbc.queryForObject("SELECT template FROM greeting_template WHERE id = 1", String.class))
                .isEqualTo("Hello, %s!");
    }
}
