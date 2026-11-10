package com.triageai.tickets.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.triageai.tickets.config.TicketsProperties;
import com.triageai.tickets.dominio.EstadoTicket;
import com.triageai.tickets.dominio.Ticket;
import com.triageai.tickets.repositorio.TicketRepository;

/** RN-03 escalation at 80% of the deadline and RN-01 automatic closing. */
class TareasProgramadasTest {

    private static final Instant T0 = Instant.parse("2026-10-13T14:00:00Z");

    private final TicketRepository repo = mock(TicketRepository.class);
    private final TicketsProperties props = new TicketsProperties();

    private TareasProgramadas tareasEn(Instant ahora) {
        return new TareasProgramadas(repo, props, Clock.fixed(ahora, ZoneOffset.UTC));
    }

    private Ticket ticketConPrimeraRespuestaEnUnaHora() {
        Ticket t = new Ticket(UUID.randomUUID(), "Login fails", "Error 500 after login", T0);
        t.aplicarPlazos(Duration.ofHours(1), Duration.ofHours(8));
        return t;
    }

    @Test
    void doesNotEscalateBefore80Percent() {
        Ticket t = ticketConPrimeraRespuestaEnUnaHora();
        when(repo.findByEscaladoFalseAndEstadoIn(any())).thenReturn(List.of(t));

        assertThat(tareasEn(T0.plus(Duration.ofMinutes(47))).escalar()).isZero();
        assertThat(t.isEscalado()).isFalse();
    }

    @Test
    void escalatesAt80PercentOfFirstResponseDeadline() {
        Ticket t = ticketConPrimeraRespuestaEnUnaHora();
        when(repo.findByEscaladoFalseAndEstadoIn(any())).thenReturn(List.of(t));

        assertThat(tareasEn(T0.plus(Duration.ofMinutes(48))).escalar()).isEqualTo(1);
        assertThat(t.isEscalado()).isTrue();
    }

    @Test
    void answeredTicketOnlyEscalatesOnResolutionDeadline() {
        Ticket t = ticketConPrimeraRespuestaEnUnaHora();
        t.responder(UUID.randomUUID(), "On it", false, T0.plus(Duration.ofMinutes(10)));
        when(repo.findByEscaladoFalseAndEstadoIn(any())).thenReturn(List.of(t));

        assertThat(tareasEn(T0.plus(Duration.ofHours(2))).escalar()).isZero();
        assertThat(tareasEn(T0.plus(Duration.ofMinutes(384))).escalar()).isEqualTo(1); // 80% of 8 h
    }

    @Test
    void closesResolvedTickets() {
        Ticket t = ticketConPrimeraRespuestaEnUnaHora();
        t.cambiarEstado(EstadoTicket.TRIADO, null, T0);
        t.cambiarEstado(EstadoTicket.EN_CURSO, null, T0);
        t.cambiarEstado(EstadoTicket.RESUELTO, null, T0);
        when(repo.findByEstadoAndResueltoEnBefore(any(), any())).thenReturn(List.of(t));

        assertThat(tareasEn(T0.plus(Duration.ofDays(8))).cerrarResueltos()).isEqualTo(1);
        assertThat(t.getEstado()).isEqualTo(EstadoTicket.CERRADO);
    }
}
