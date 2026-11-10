package com.triageai.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.triageai.agente.triaje.ServicioTriaje;

/**
 * Starts the whole agent against real PostgreSQL (Flyway schema "agente") and
 * Redis: a ticket.creado event put on the stream is consumed and acknowledged.
 * The triage itself is mocked: no LLM call is made.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.ai.anthropic.api-key=test-key"})
@AutoConfigureMockMvc
@Import(AgenteIntegracionTests.Contenedores.class)
class AgenteIntegracionTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class Contenedores {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        }

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
        }
    }

    @MockitoBean
    ServicioTriaje triaje;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    MockMvc mvc;

    @Test
    void consumesAndAcknowledgesTicketCreatedEvents() throws InterruptedException {
        UUID ticketId = UUID.randomUUID();

        redis.opsForStream().add(StreamRecords.string(Map.of(
                "eventoId", UUID.randomUUID().toString(), "tipo", "ticket.creado", "version", "1",
                "ticketId", ticketId.toString(), "ocurridoEn", "2026-10-13T14:05:00Z"))
                .withStreamKey("ticket-events"));

        verify(triaje, timeout(15_000)).triar(ticketId);
        // XACK happens right after triar() returns
        long pendientes = 1;
        for (int i = 0; i < 50 && pendientes > 0; i++) {
            pendientes = redis.opsForStream().pending("ticket-events", "triaje").getTotalPendingMessages();
            if (pendientes > 0) {
                Thread.sleep(100);
            }
        }
        assertThat(pendientes).isZero();
    }

    @Test
    void providerStatusStartsClosed() throws Exception {
        mvc.perform(get("/triaje/proveedor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor").value("ANTHROPIC"))
                .andExpect(jsonPath("$.circuito").value("CLOSED"))
                .andExpect(jsonPath("$.tasaFallos").value(0.0));
    }

    @Test
    void metricsQueryRunsAgainstTheAgentSchema() throws Exception {
        mvc.perform(get("/triaje/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llamadas").value(0));
    }
}
