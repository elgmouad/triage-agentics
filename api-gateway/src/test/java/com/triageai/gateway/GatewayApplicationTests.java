package com.triageai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.sun.net.httpserver.HttpServer;

/**
 * Routing and rate limiting against a stub "servicio-tickets" (registered
 * through the simple discovery client) and a real Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "gateway.rate-limit.replenish-rate=1",
        "gateway.rate-limit.burst-capacity=5"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(GatewayApplicationTests.Contenedores.class)
class GatewayApplicationTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class Contenedores {
        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
        }
    }

    /** Fake servicio-tickets that echoes the path it received. */
    static final HttpServer TICKETS = iniciarStub();

    static HttpServer iniciarStub() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            s.createContext("/", ex -> {
                byte[] body = ("path=" + ex.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(body);
                }
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void descubrimiento(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.servicio-tickets[0].uri",
                () -> "http://localhost:" + TICKETS.getAddress().getPort());
    }

    @AfterAll
    static void parar() {
        TICKETS.stop(0);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String ip) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Forwarded-For", ip).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void stripsApiPrefixAndRoutesThroughDiscovery() throws Exception {
        HttpResponse<String> r = get("/api/v1/tickets/123/historial", "10.0.0.1");

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).isEqualTo("path=/tickets/123/historial");
    }

    @Test
    void internalApiIsNeverExposed() throws Exception {
        assertThat(get("/internal/tickets/123", "10.0.0.2").statusCode()).isEqualTo(404);
    }

    @Test
    void unavailableServiceGives503() throws Exception {
        // servicio-agente-triaje has no registered instance in this test
        assertThat(get("/api/v1/triaje/proveedor", "10.0.0.3").statusCode()).isEqualTo(503);
    }

    @Test
    @Order(Integer.MAX_VALUE) // runs last: it empties the shared bucket
    void rateLimitsAndIgnoresForgedForwardedFor() throws Exception {
        int rechazadas = 0;
        for (int i = 0; i < 12; i++) {
            // A different X-Forwarded-For each time must not reset the client's bucket
            if (get("/api/v1/tickets", "10.0.1." + i).statusCode() == 429) {
                rechazadas++;
            }
        }
        assertThat(rechazadas).isGreaterThan(0);
    }
}
