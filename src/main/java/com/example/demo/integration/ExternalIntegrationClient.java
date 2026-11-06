package com.example.demo.integration;

import com.example.demo.config.IntegrationProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** An explicitly enabled, fixed-URL GET client. No request is made during startup. */
@Component
public class ExternalIntegrationClient {
    private final IntegrationProperties properties;
    private final HttpClient client;
    private String cachedBody;
    private Instant cacheExpiresAt = Instant.EPOCH;

    public ExternalIntegrationClient(IntegrationProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public synchronized String fetch() throws IOException, InterruptedException {
        if (!properties.enabled()) {
            throw new IllegalStateException("External integration is disabled");
        }
        if (cachedBody != null && Instant.now().isBefore(cacheExpiresAt)) {
            return cachedBody;
        }
        var request = HttpRequest.newBuilder(properties.url()).timeout(properties.requestTimeout()).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("External integration returned HTTP " + response.statusCode());
        }
        if (!properties.cacheTtl().isZero()) {
            cachedBody = response.body();
            cacheExpiresAt = Instant.now().plus(properties.cacheTtl());
        }
        return response.body();
    }
}
