package com.anymind.promptrecorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointWorks() {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.getForObject(
            "http://localhost:" + port + "/health", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.get("ok")).isEqualTo(true);
    }
}
