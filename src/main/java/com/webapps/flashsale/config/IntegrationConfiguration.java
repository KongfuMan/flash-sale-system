package com.webapps.flashsale.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationConfiguration {
}
