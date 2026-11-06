package com.example.demo.repository;

import com.example.demo.dao.GreetingDao;
import com.example.demo.mapper.GreetingTemplateMapper;
import org.springframework.stereotype.Repository;

@Repository
public class GreetingRepository {
    private final GreetingDao greetingDao;

    public GreetingRepository(GreetingDao greetingDao) {
        this.greetingDao = greetingDao;
    }

    public String getTemplate() {
        return GreetingTemplateMapper.toDomain(greetingDao.loadTemplate()).template();
    }
}
