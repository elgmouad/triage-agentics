package com.triageai.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** End-to-end tests of the REST API against real PostgreSQL and Redis (Testcontainers). */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class TicketsApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    StringRedisTemplate redis;

    private final UUID cliente = UUID.randomUUID();

    @Test
    void createsTicketAndPublishesEvent() throws Exception {
        String id = crear("Cannot log into my account",
                "Since this morning I get a 500 error after my password. The whole team is blocked.");

        mvc.perform(get("/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NUEVO"))
                .andExpect(jsonPath("$.sla.primeraRespuestaAntes").exists());

        List<MapRecord<String, Object, Object>> eventos = redis.opsForStream()
                .read(StreamOffset.fromStart("ticket-events"));
        assertThat(eventos).anySatisfy(e -> {
            assertThat(e.getValue()).containsEntry("ticketId", id);
            assertThat(e.getValue()).containsEntry("tipo", "ticket.creado");
            assertThat(e.getValue()).containsEntry("version", "1");
        });
    }

    @Test
    void rejectsInvalidTicketWithProblemDetails() throws Exception {
        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"" + cliente + "\",\"titulo\":\"Hi\",\"descripcion\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")));
    }

    @Test
    void unknownTicketIs404() throws Exception {
        mvc.perform(get("/tickets/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://triageai.example/errores/ticket-no-encontrado"));
    }

    @Test
    void rejectsTransitionNotAllowedByRn01() throws Exception {
        String id = crear("Cannot log into my account", "Error 500 right after entering my password.");

        mvc.perform(patch("/tickets/" + id + "/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RESUELTO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot move from NUEVO to RESUELTO."))
                .andExpect(jsonPath("$.transicionesPermitidas", containsInAnyOrder("TRIADO", "PENDIENTE_TRIAJE")));
    }

    @Test
    void fullLifecycleWithAiTriage() throws Exception {
        String id = crear("Cannot log into my account", "Error 500 right after entering my password.");

        // Agent records a low-confidence AI triage -> TRIADO + manual review (RN-04), SLA CRITICA (RN-02)
        String creadoEn = JsonPath.read(mvc.perform(get("/tickets/" + id)).andReturn()
                .getResponse().getContentAsString(), "$.creadoEn");
        registrarTriaje(id, """
                {"modo":"IA","categoria":"INCIDENTE","prioridad":"CRITICA","confianza":0.6,
                 "resumen":"Login error","borradorRespuesta":"Hello...",
                 "trazabilidad":{"proveedor":"ANTHROPIC","modelo":"m","tokensEntrada":812,
                                 "tokensSalida":164,"latenciaMs":6430,"intentos":1}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("TRIADO"))
                .andExpect(jsonPath("$.revisionManual").value(true))
                .andExpect(jsonPath("$.triaje.modo").value("IA"))
                .andExpect(jsonPath("$.triaje.confianza").value(0.6));
        String limite = JsonPath.read(mvc.perform(get("/tickets/" + id)).andReturn()
                .getResponse().getContentAsString(), "$.sla.primeraRespuestaAntes");
        assertThat(java.time.Duration.between(java.time.Instant.parse(creadoEn), java.time.Instant.parse(limite)))
                .hasHours(1);

        // A second triage result for the same ticket is rejected
        registrarTriaje(id, "{\"modo\":\"REGLAS\",\"motivoFallo\":\"x\"}").andExpect(status().isConflict());

        // Human correction recomputes the SLA and clears the manual review flag
        mvc.perform(patch("/tickets/" + id + "/triaje").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"CUENTA\",\"prioridad\":\"ALTA\",\"motivo\":\"single account\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("CUENTA"))
                .andExpect(jsonPath("$.revisionManual").value(false))
                .andExpect(jsonPath("$.triaje.corregidoPorHumano").value(true));

        // Validated reply -> EN_CURSO (RN-05)
        mvc.perform(post("/tickets/" + id + "/respuesta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agenteId\":\"" + UUID.randomUUID() + "\",\"texto\":\"Try again\",\"basadoEnBorrador\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"));

        cambiarEstado(id, "RESUELTO").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RESUELTO"));

        mvc.perform(post("/tickets/" + id + "/reapertura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"));

        mvc.perform(get("/tickets/" + id + "/historial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].estadoNuevo").value("NUEVO"))
                .andExpect(jsonPath("$[4].comentario").value("Reopened by the customer"));
    }

    @Test
    void ruleBasedFallbackWhenAiIsUnavailable() throws Exception {
        String id = crear("Factura duplicada", "Me habéis cobrado dos veces la suscripción de octubre.");

        registrarTriaje(id, """
                {"modo":"REGLAS","motivoFallo":"ANTHROPIC: HTTP 429 (circuit OPEN)",
                 "trazabilidad":{"proveedor":"NINGUNO","intentos":3}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_TRIAJE"))
                .andExpect(jsonPath("$.categoria").value("FACTURACION"))
                .andExpect(jsonPath("$.prioridad").value("ALTA"))
                .andExpect(jsonPath("$.revisionManual").value(true))
                .andExpect(jsonPath("$.triaje.modo").value("REGLAS"))
                .andExpect(jsonPath("$.triaje.proveedor").value("NINGUNO"));

        // No reply is possible before a real triage
        mvc.perform(post("/tickets/" + id + "/respuesta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agenteId\":\"" + UUID.randomUUID() + "\",\"texto\":\"Hi\"}"))
                .andExpect(status().isConflict());

        // A later successful retry moves it to TRIADO
        registrarTriaje(id, """
                {"modo":"IA","categoria":"FACTURACION","prioridad":"MEDIA","confianza":0.95}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("TRIADO"))
                .andExpect(jsonPath("$.revisionManual").value(false));
    }

    @Test
    void customerHistoryAndFilters() throws Exception {
        crear("First ticket title", "A first description long enough.");
        crear("Second ticket title", "A second description long enough.");

        mvc.perform(get("/internal/clientes/" + cliente + "/historial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(2))
                .andExpect(jsonPath("$.ticketsRecientes", hasSize(2)))
                .andExpect(jsonPath("$.ticketsRecientes[0].titulo").value("Second ticket title"));

        mvc.perform(get("/tickets").param("clienteId", cliente.toString()).param("estado", "NUEVO").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.tamano").value(1))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    // ------------------------------------------------------------------ helpers

    private String crear(String titulo, String descripcion) throws Exception {
        String body = mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"" + cliente + "\",\"titulo\":\"" + titulo
                                + "\",\"descripcion\":\"" + descripcion + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/tickets/")))
                .andExpect(jsonPath("$.estado").value("NUEVO"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions registrarTriaje(String id, String json) throws Exception {
        return mvc.perform(put("/internal/tickets/" + id + "/triaje")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions cambiarEstado(String id, String estado) throws Exception {
        return mvc.perform(patch("/tickets/" + id + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\"}"));
    }
}
