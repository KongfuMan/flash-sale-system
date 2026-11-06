package com.webapps.flashsale.integration;

import com.webapps.flashsale.config.IntegrationProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalIntegrationClientTest {
    @Test
    void disabledClientDoesNotMakeRequests() {
        var properties = new IntegrationProperties(false, URI.create("https://example.com"), null, null, null);
        var client = new ExternalIntegrationClient(properties);
        assertThatThrownBy(client::fetch).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enabledClientRequiresHttpsAndValidTimeouts() {
        assertThatThrownBy(() -> new IntegrationProperties(true, URI.create("http://example.com"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegrationProperties(false, null, Duration.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
