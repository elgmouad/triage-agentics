package com.triageai.agente.triaje;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.triageai.agente.config.LlmProperties;
import com.triageai.agente.ia.LlamadaResiliente;
import com.triageai.agente.ia.RespuestaIa;
import com.triageai.agente.ia.ResultadoTriaje;
import com.triageai.agente.tickets.ClienteTickets;
import com.triageai.agente.tickets.RegistroTriaje;
import com.triageai.agente.tickets.TicketDto;

/**
 * Triage of one ticket (README section 6.4): read it, ask the LLM (or the
 * cache), record the call (RN-07) and send the result to servicio-tickets —
 * IA on success, REGLAS fallback otherwise (RN-06).
 */
@Service
public class ServicioTriaje {

    private static final Logger log = LoggerFactory.getLogger(ServicioTriaje.class);
    private static final String ANTHROPIC = "ANTHROPIC";

    public enum Resultado { IA, IA_CACHE, REGLAS, OMITIDO }

    private final ClienteTickets tickets;
    private final LlamadaResiliente llamada;
    private final CacheTriaje cache;
    private final LlamadaLlmRepository llamadas;
    private final LlmProperties llm;
    private final Clock clock;

    public ServicioTriaje(ClienteTickets tickets, LlamadaResiliente llamada, CacheTriaje cache,
                          LlamadaLlmRepository llamadas, LlmProperties llm, Clock clock) {
        this.tickets = tickets;
        this.llamada = llamada;
        this.cache = cache;
        this.llamadas = llamadas;
        this.llm = llm;
        this.clock = clock;
    }

    /**
     * @throws com.triageai.agente.tickets.TicketNoEncontradoException if the ticket does not exist
     * @throws RuntimeException if servicio-tickets is unreachable (the event stays pending)
     */
    public Resultado triar(UUID ticketId) {
        TicketDto ticket = tickets.obtener(ticketId);
        if (!ticket.esperaTriaje()) {
            log.info("Ticket {} is {}: nothing to triage", ticketId, ticket.estado());
            return Resultado.OMITIDO;
        }

        Optional<ResultadoTriaje> enCache = cache.buscar(ticket);
        if (enCache.isPresent()) {
            llamadas.save(new LlamadaLlm(ticketId, ANTHROPIC, llm.modelo(), true, true, 0, 0, 0, 0, null,
                    clock.instant()));
            tickets.registrarTriaje(ticketId, RegistroTriaje.ia(enCache.get(),
                    new RegistroTriaje.Trazabilidad(ANTHROPIC, llm.modelo() + " (cache)", 0, 0, 0L, 0)));
            log.info("Ticket {} triaged from cache", ticketId);
            return Resultado.IA_CACHE;
        }

        LlamadaResiliente.Resultado r = llamada.llamar(ticket);
        RespuestaIa ia = r.respuesta();
        llamadas.save(new LlamadaLlm(ticketId, ANTHROPIC, llm.modelo(), r.exito(), false,
                ia == null ? null : ia.tokensEntrada(), ia == null ? null : ia.tokensSalida(),
                r.latenciaMs(), r.intentos(), r.motivoFallo(), clock.instant()));

        if (r.exito()) {
            cache.guardar(ticket, ia.resultado());
            tickets.registrarTriaje(ticketId, RegistroTriaje.ia(ia.resultado(), new RegistroTriaje.Trazabilidad(
                    ANTHROPIC, llm.modelo(), ia.tokensEntrada(), ia.tokensSalida(), r.latenciaMs(), r.intentos())));
            log.info("Ticket {} triaged by AI: {} / {} (confidence {})", ticketId,
                    ia.resultado().categoria(), ia.resultado().prioridad(), ia.resultado().confianza());
            return Resultado.IA;
        }
        tickets.registrarTriaje(ticketId, RegistroTriaje.reglas(r.motivoFallo(), r.intentos()));
        log.info("Ticket {} sent to rule-based triage: {}", ticketId, r.motivoFallo());
        return Resultado.REGLAS;
    }
}
