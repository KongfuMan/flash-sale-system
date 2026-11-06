package com.webapps.flashsale.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/v1/greeting", "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/v1/greeting", "/actuator/health").permitAll()
                .anyRequest().denyAll())
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new BadCredentialsException("Authentication is not configured");
        };
    }
}
