package com.triageai.agente.web;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.triageai.agente.ia.LlamadaResiliente;
import com.triageai.agente.tickets.ClienteTickets;
import com.triageai.agente.tickets.TicketDto;
import com.triageai.agente.triaje.LlamadaLlmRepository;
import com.triageai.agente.triaje.ServicioTriaje;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/** Public API of the agent (README section 7.3), exposed by the gateway as /api/v1/triaje/**. */
@RestController
@RequestMapping("/triaje")
public class TriajeController {

    private static final Logger log = LoggerFactory.getLogger(TriajeController.class);

    private final LlamadaResiliente llamada;
    private final LlamadaLlmRepository llamadas;
    private final ClienteTickets tickets;
    private final ServicioTriaje triaje;
    private final TaskExecutor ejecutor;
    private final Clock clock;

    public TriajeController(LlamadaResiliente llamada, LlamadaLlmRepository llamadas, ClienteTickets tickets,
                            ServicioTriaje triaje, @Qualifier("applicationTaskExecutor") TaskExecutor ejecutor,
                            Clock clock) {
        this.llamada = llamada;
        this.llamadas = llamadas;
        this.tickets = tickets;
        this.triaje = triaje;
        this.ejecutor = ejecutor;
        this.clock = clock;
    }

    public record EstadoProveedor(String proveedor, String circuito, double tasaFallos, long latenciaMediaMs) {
    }

    /** CU-08 — LLM provider status and its circuit breaker. */
    @GetMapping("/proveedor")
    public EstadoProveedor proveedor() {
        CircuitBreaker cb = llamada.circuito();
        float tasa = cb.getMetrics().getFailureRate(); // -1 until enough calls were recorded
        Instant ahora = clock.instant();
        Double latencia = llamadas.resumir(ahora.minus(Duration.ofHours(24)), ahora).getLatenciaMediaMs();
        return new EstadoProveedor("ANTHROPIC", cb.getState().name(),
                tasa < 0 ? 0 : Math.round(tasa * 10) / 1000.0,
                latencia == null ? 0 : Math.round(latencia));
    }

    /** CU-07 / HU-08 — manual retry, processed in the background. */
    @PostMapping("/tickets/{id}/reintento")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> reintentar(@PathVariable UUID id) {
        TicketDto ticket = tickets.obtener(id); // 404 if unknown
        if (!ticket.esperaTriaje()) {
            throw new ConflictoException("Ticket " + id + " is " + ticket.estado()
                    + ": only NUEVO or PENDIENTE_TRIAJE tickets can be retried.");
        }
        ejecutor.execute(() -> {
            try {
                triaje.triar(id);
            } catch (RuntimeException e) {
                log.warn("Manual retry of ticket {} failed: {}", id, e.getMessage());
            }
        });
        return Map.of("ticketId", id, "estado", "ACEPTADO");
    }

    public record Metricas(Instant desde, Instant hasta, long llamadas, long fallos, double tasaFallos,
                           long tokensEntrada, long tokensSalida, long latenciaMediaMs) {
    }

    /** CU-08 (extension) — AI consumption over a period (default: last 24 h). */
    @GetMapping("/metricas")
    public Metricas metricas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {
        Instant fin = hasta == null ? clock.instant() : hasta;
        Instant inicio = desde == null ? fin.minus(Duration.ofHours(24)) : desde;
        LlamadaLlmRepository.Resumen r = llamadas.resumir(inicio, fin);
        long fallos = r.getFallos() == null ? 0 : r.getFallos();
        return new Metricas(inicio, fin, r.getLlamadas(), fallos,
                r.getLlamadas() == 0 ? 0 : (double) fallos / r.getLlamadas(),
                r.getTokensEntrada() == null ? 0 : r.getTokensEntrada(),
                r.getTokensSalida() == null ? 0 : r.getTokensSalida(),
                r.getLatenciaMediaMs() == null ? 0 : Math.round(r.getLatenciaMediaMs()));
    }
}
