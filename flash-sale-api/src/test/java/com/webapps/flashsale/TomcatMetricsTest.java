package com.webapps.flashsale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.endpoints.web.exposure.include=health,prometheus",
        "server.tomcat.mbeanregistry.enabled=true",
        "management.metrics.distribution.percentiles-histogram.http.server.requests=true"
})
@AutoConfigureObservability
class TomcatMetricsTest {
    @Autowired private TestRestTemplate http;

    @Test
    void exposesHttpWorkerThreadMetrics() {
        var response = http.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("tomcat_threads_busy_threads",
                "tomcat_threads_current_threads", "tomcat_threads_config_max_threads");
    }

    @Test
    void exposesHttpLatencyHistogram() {
        http.getForEntity("/actuator/health", String.class);
        var response = http.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getBody()).contains("http_server_requests_seconds_bucket");
    }
}
