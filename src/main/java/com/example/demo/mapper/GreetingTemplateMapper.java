package com.example.demo.mapper;

import com.example.demo.domain.GreetingTemplate;
import com.example.demo.entity.GreetingTemplateEntity;

public final class GreetingTemplateMapper {
    private GreetingTemplateMapper() {}

    public static GreetingTemplate toDomain(GreetingTemplateEntity entity) {
        return new GreetingTemplate(entity.template());
    }

    public static GreetingTemplateEntity toEntity(GreetingTemplate template) {
        return new GreetingTemplateEntity(template.template());
    }
}
