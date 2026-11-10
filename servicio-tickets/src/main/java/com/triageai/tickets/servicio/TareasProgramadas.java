package com.triageai.tickets.servicio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.triageai.tickets.config.TicketsProperties;
import com.triageai.tickets.dominio.EstadoTicket;
import com.triageai.tickets.dominio.Ticket;
import com.triageai.tickets.repositorio.TicketRepository;

/** Scheduled jobs: SLA escalation (RN-03) and automatic closing (RN-01). */
@Component
public class TareasProgramadas {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);

    /** States in which an SLA clock can still be running. */
    private static final EnumSet<EstadoTicket> ABIERTOS = EnumSet.of(EstadoTicket.NUEVO,
            EstadoTicket.PENDIENTE_TRIAJE, EstadoTicket.TRIADO, EstadoTicket.EN_CURSO);

    private final TicketRepository tickets;
    private final TicketsProperties props;
    private final Clock clock;

    public TareasProgramadas(TicketRepository tickets, TicketsProperties props, Clock clock) {
        this.tickets = tickets;
        this.props = props;
        this.clock = clock;
    }

    /** RN-03: flags tickets that consumed 80% of a deadline without meeting it. */
    @Scheduled(fixedDelayString = "${tickets.escalado.frecuencia-ms:60000}", initialDelay = 30_000)
    @Transactional
    public int escalar() {
        Instant ahora = clock.instant();
        int escalados = 0;
        for (Ticket ticket : tickets.findByEscaladoFalseAndEstadoIn(ABIERTOS)) {
            boolean sinRespuesta = ticket.getPrimeraRespuestaEn() == null
                    && umbralAlcanzado(ticket.getCreadoEn(), ticket.getPrimeraRespuestaAntes(), ahora);
            boolean sinResolver = ticket.getResueltoEn() == null
                    && umbralAlcanzado(ticket.getCreadoEn(), ticket.getResolucionAntes(), ahora);
            if (sinRespuesta || sinResolver) {
                ticket.marcarEscalado(ahora);
                escalados++;
                // Supervisor alert: a log line for now, notifications are out of the MVP scope.
                log.warn("SLA alert: ticket {} escalated ({} priority, {} deadline at 80%)", ticket.getId(),
                        ticket.getPrioridad(), sinRespuesta ? "first response" : "resolution");
            }
        }
        return escalados;
    }

    /** RN-01: RESUELTO tickets not reopened within N days become CERRADO. */
    @Scheduled(fixedDelayString = "${tickets.cierre-automatico.frecuencia-ms:3600000}", initialDelay = 60_000)
    @Transactional
    public int cerrarResueltos() {
        Instant ahora = clock.instant();
        Instant limite = ahora.minus(Duration.ofDays(props.getCierreAutomatico().getDias()));
        int cerrados = 0;
        for (Ticket ticket : tickets.findByEstadoAndResueltoEnBefore(EstadoTicket.RESUELTO, limite)) {
            ticket.cambiarEstado(EstadoTicket.CERRADO, "Closed automatically after "
                    + props.getCierreAutomatico().getDias() + " days without reopening", ahora);
            cerrados++;
        }
        if (cerrados > 0) {
            log.info("Closed {} resolved tickets automatically", cerrados);
        }
        return cerrados;
    }

    private boolean umbralAlcanzado(Instant inicio, Instant limite, Instant ahora) {
        if (limite == null) {
            return false;
        }
        long total = Duration.between(inicio, limite).toMillis();
        long transcurrido = Duration.between(inicio, ahora).toMillis();
        return transcurrido >= total * props.getEscalado().getUmbral();
    }
}
