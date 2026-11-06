package com.webapps.flashsale;

import com.webapps.flashsale.dto.FlashSaleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlashSaleHttpTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void servesARealHttpRequest() {
        var response = http.getForEntity("/api/v1/greeting?name=Charles", FlashSaleResponse.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Hello, Charles!");
    }
}
