package com.triageai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the server against the real config-repo/ folder and checks that
 * the per-service files are served.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.config.server.native.search-locations=file:../config-repo")
class ConfigServerApplicationTests {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesAgentConfigurationWithConfidenceThreshold() throws Exception {
        HttpResponse<String> response = get("/servicio-agente-triaje/default");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("servicio-agente-triaje.yml")
                .contains("llm.umbral-confianza")
                .contains("application.yml");
    }

    @Test
    void servesEveryBusinessAndGatewayService() throws Exception {
        for (String service : new String[] {"servicio-tickets", "api-gateway"}) {
            HttpResponse<String> response = get("/" + service + "/default");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains(service + ".yml");
        }
    }
}
