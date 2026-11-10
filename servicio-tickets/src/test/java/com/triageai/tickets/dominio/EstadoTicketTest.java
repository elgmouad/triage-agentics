package com.triageai.tickets.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** RN-01 state machine. */
class EstadoTicketTest {

    @ParameterizedTest
    @CsvSource({
            "NUEVO, TRIADO", "NUEVO, PENDIENTE_TRIAJE", "PENDIENTE_TRIAJE, TRIADO",
            "TRIADO, EN_CURSO", "EN_CURSO, RESUELTO", "RESUELTO, EN_CURSO", "RESUELTO, CERRADO"})
    void allowsTransitionsOfTheDiagram(EstadoTicket origen, EstadoTicket destino) {
        assertThat(origen.puedePasarA(destino)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "NUEVO, RESUELTO", "NUEVO, EN_CURSO", "TRIADO, RESUELTO", "EN_CURSO, CERRADO",
            "CERRADO, EN_CURSO", "PENDIENTE_TRIAJE, EN_CURSO", "TRIADO, NUEVO"})
    void rejectsAnyOtherTransition(EstadoTicket origen, EstadoTicket destino) {
        assertThat(origen.puedePasarA(destino)).isFalse();
    }

    @Test
    void cerradoIsFinal() {
        assertThat(EstadoTicket.CERRADO.transicionesPermitidas()).isEmpty();
    }

    @Test
    void ticketRecordsHistoryAndRejectsInvalidChange() {
        Instant t0 = Instant.parse("2026-10-13T14:05:00Z");
        Ticket ticket = new Ticket(UUID.randomUUID(), "Login fails", "Error 500 after login", t0);

        ticket.cambiarEstado(EstadoTicket.TRIADO, "AI", t0.plusSeconds(5));

        assertThat(ticket.getHistorial()).extracting(HistorialEstado::getEstadoNuevo)
                .containsExactly(EstadoTicket.NUEVO, EstadoTicket.TRIADO);
        assertThatThrownBy(() -> ticket.cambiarEstado(EstadoTicket.RESUELTO, null, t0))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessage("Cannot move from TRIADO to RESUELTO.");
    }

    @Test
    void resolvingAndReopeningManagesResolutionDate() {
        Instant t0 = Instant.parse("2026-10-13T14:05:00Z");
        Ticket ticket = new Ticket(UUID.randomUUID(), "Login fails", "Error 500 after login", t0);
        ticket.cambiarEstado(EstadoTicket.TRIADO, null, t0);
        ticket.cambiarEstado(EstadoTicket.EN_CURSO, null, t0);

        ticket.cambiarEstado(EstadoTicket.RESUELTO, null, t0.plusSeconds(60));
        assertThat(ticket.getResueltoEn()).isEqualTo(t0.plusSeconds(60));

        ticket.cambiarEstado(EstadoTicket.EN_CURSO, "reopened", t0.plusSeconds(120));
        assertThat(ticket.getResueltoEn()).isNull();
    }
}
