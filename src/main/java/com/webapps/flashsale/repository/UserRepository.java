package com.webapps.flashsale.repository;

import com.webapps.flashsale.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {}
