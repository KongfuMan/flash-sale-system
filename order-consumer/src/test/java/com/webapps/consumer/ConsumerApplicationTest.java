package com.webapps.consumer;

import com.webapps.consumer.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ConsumerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.kafka.listener.auto-startup=false")
class ConsumerApplicationTest {
    @Autowired
    ApplicationContext context;

    @Test
    void startsWithoutApiControllers() {
        assertNotNull(context.getBean(OrderService.class));
        assertNotNull(context.getBean(MessageListener.class));
        assertTrue(context.getBeansWithAnnotation(Controller.class).isEmpty());
        assertFalse(context.getResource("classpath:redis/reserve.lua").exists());
        assertFalse(context.getResource("classpath:db/migration/V1__initial_flash_sale_schema.sql").exists());
    }
}
