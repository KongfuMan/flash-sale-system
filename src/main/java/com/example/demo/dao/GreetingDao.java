package com.example.demo.dao;

import com.example.demo.entity.GreetingTemplateEntity;
import org.springframework.stereotype.Component;

@Component
public class GreetingDao {
    public GreetingTemplateEntity loadTemplate() {
        return new GreetingTemplateEntity("Hello, %s!");
    }
}
