package com.example.demo.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.integration")
public record IntegrationProperties(boolean enabled, URI url, Duration connectTimeout,
        Duration requestTimeout, Duration cacheTtl) {
    public IntegrationProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
        cacheTtl = cacheTtl == null ? Duration.ZERO : cacheTtl;
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || requestTimeout.isNegative() || requestTimeout.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("Integration timeouts must be positive and cache TTL non-negative");
        }
        if (enabled && (url == null || !"https".equalsIgnoreCase(url.getScheme())
                || url.getHost() == null || url.getUserInfo() != null || url.getFragment() != null)) {
            throw new IllegalArgumentException("Enabled integration requires an HTTPS URL without user info or fragment");
        }
    }
}
