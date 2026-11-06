package com.webapps.flashsale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "greeting_template")
public class FlashSaleTemplateEntity {
    @Id
    private Long id;

    @Column(nullable = false, length = 255)
    private String template;

    protected FlashSaleTemplateEntity() {}

    public FlashSaleTemplateEntity(Long id, String template) {
        this.id = id;
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}
