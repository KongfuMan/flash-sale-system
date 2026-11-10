package com.webapps.flashsale.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "users")
@DynamicInsert
public class UserEntity {
    @Id @Column(name = "user_id", nullable = false)
    public Long userId;
    @Column(nullable = false, length = 50)
    public String username;
    @JsonIgnore
    @Column(name = "password_hash", length = 255)
    public String passwordHash;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
