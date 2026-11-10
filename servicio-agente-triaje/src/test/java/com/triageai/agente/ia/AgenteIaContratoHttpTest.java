package com.triageai.agente.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;

import com.sun.net.httpserver.HttpServer;
import com.triageai.agente.config.LlmProperties;
import com.triageai.agente.tickets.ClienteTickets;
import com.triageai.agente.tickets.TicketDto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wire contract with the Anthropic Messages API, checked against a local stub
 * server (no real API call): request parameters accepted by current models,
 * the tool-calling round trip, and thinking blocks passed back unchanged.
 */
class AgenteIaContratoHttpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UUID clienteId = UUID.randomUUID();
    private final List<JsonNode> peticiones = new CopyOnWriteArrayList<>();
    private HttpServer servidor;

    @BeforeEach
    void arrancarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/v1/messages", intercambio -> {
            peticiones.add(JSON.readTree(intercambio.getRequestBody().readAllBytes()));
            String respuesta = peticiones.size() == 1 ? llamadaHerramienta() : respuestaFinal();
            byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream out = intercambio.getResponseBody()) {
                out.write(cuerpo);
            }
        });
        servidor.start();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    private String llamadaHerramienta() {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[
                   {"type":"thinking","thinking":"","signature":"firma-123"},
                   {"type":"tool_use","id":"toolu_1","name":"obtenerHistorialCliente",
                    "input":{"clienteId":"%s"}}],
                 "stop_reason":"tool_use","stop_sequence":null,
                 "usage":{"input_tokens":500,"output_tokens":40}}""".formatted(clienteId);
    }

    private String respuestaFinal() {
        String triaje = "{\\\"categoria\\\":\\\"CUENTA\\\",\\\"prioridad\\\":\\\"ALTA\\\","
                + "\\\"resumen\\\":\\\"Repeated login failure\\\",\\\"confianza\\\":0.88,"
                + "\\\"borradorRespuesta\\\":\\\"Hello, we are looking into it.\\\"}";
        return """
                {"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":"%s"}],
                 "stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":700,"output_tokens":90}}""".formatted(triaje);
    }

    @Test
    void sendsValidParametersRunsToolAndParsesAnswer() {
        ClienteTickets tickets = mock(ClienteTickets.class);
        when(tickets.historialCliente(clienteId)).thenReturn(Map.of("totalTickets", 3));
        AnthropicChatModel modelo = AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder()
                        .baseUrl("http://localhost:" + servidor.getAddress().getPort())
                        .apiKey("test-key")
                        .maxRetries(0)
                        .build())
                .build();
        AgenteIa agente = new AgenteIa(modelo, new HerramientasTriaje(tickets),
                new LlmProperties(null, "claude-opus-5-5", null, null, "low", 16000));

        RespuestaIa r = agente.triar(new TicketDto(UUID.randomUUID(), clienteId, "Cannot log in",
                "Same login error as last week.", "NUEVO", 0));

        // Final answer parsed
        assertThat(r.resultado().categoria()).isEqualTo(ResultadoTriaje.Categoria.CUENTA);
        assertThat(r.resultado().confianza()).isEqualTo(0.88);
        // Tool executed against servicio-tickets
        verify(tickets).historialCliente(clienteId);
        assertThat(peticiones).hasSize(2);

        // First request: parameters accepted by current models
        JsonNode primera = peticiones.getFirst();
        assertThat(primera.path("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(primera.path("max_tokens").asInt()).isEqualTo(16000);
        assertThat(primera.path("output_config").path("effort").asString()).isEqualTo("low");
        assertThat(primera.has("temperature")).as("temperature is rejected").isFalse();
        assertThat(primera.has("top_p")).isFalse();
        assertThat(primera.path("thinking").path("type").asString("adaptive"))
                .as("thinking cannot be disabled").isNotIn("disabled", "enabled");
        assertThat(primera.path("tool_choice").path("type").asString("auto"))
                .as("forced tool use is rejected").isEqualTo("auto");
        assertThat(primera.path("tools").findValuesAsString("name")).contains("obtenerHistorialCliente");

        // Second request: thinking block and tool_use replayed unchanged, then the tool_result
        String segunda = peticiones.get(1).path("messages").toString();
        assertThat(segunda).contains("firma-123", "toolu_1", "tool_result", "totalTickets");
    }
}
