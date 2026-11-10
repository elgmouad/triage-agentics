package com.triageai.agente.triaje;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.triageai.agente.config.AgenteProperties;
import com.triageai.agente.ia.LlamadaResiliente;
import com.triageai.agente.tickets.ClienteTickets;
import com.triageai.agente.tickets.TicketDto;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * HU-08 — periodically retries the AI triage of PENDIENTE_TRIAJE tickets,
 * up to a configurable number of attempts per ticket. Skipped while the
 * circuit is OPEN: the API is known to be down.
 */
@Component
public class ReintentoProgramado {

    private static final Logger log = LoggerFactory.getLogger(ReintentoProgramado.class);
    private static final int LOTE = 20;

    private final ClienteTickets tickets;
    private final ServicioTriaje triaje;
    private final LlamadaResiliente llamada;
    private final AgenteProperties props;

    public ReintentoProgramado(ClienteTickets tickets, ServicioTriaje triaje, LlamadaResiliente llamada,
                               AgenteProperties props) {
        this.tickets = tickets;
        this.triaje = triaje;
        this.llamada = llamada;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${agente.reintento.frecuencia-ms:300000}", initialDelay = 60_000)
    public int reintentar() {
        if (llamada.circuito().getState() == CircuitBreaker.State.OPEN) {
            log.debug("Circuit OPEN: scheduled retry skipped");
            return 0;
        }
        List<TicketDto> pendientes;
        try {
            pendientes = tickets.pendientesDeTriaje(LOTE);
        } catch (RuntimeException e) {
            log.warn("Could not list PENDIENTE_TRIAJE tickets: {}", e.getMessage());
            return 0;
        }
        int reintentados = 0;
        for (TicketDto ticket : pendientes) {
            if (ticket.intentosTriaje() >= props.reintento().maxIntentos()) {
                continue; // left for a human
            }
            try {
                triaje.triar(ticket.id());
                reintentados++;
            } catch (RuntimeException e) {
                log.warn("Retry of ticket {} failed: {}", ticket.id(), e.getMessage());
            }
            if (llamada.circuito().getState() == CircuitBreaker.State.OPEN) {
                break;
            }
        }
        return reintentados;
    }
}
