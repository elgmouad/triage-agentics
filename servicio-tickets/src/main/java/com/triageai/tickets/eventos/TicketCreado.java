package com.triageai.tickets.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code ticket.creado} event (README section 7.4). It only carries the
 * identifier: the agent reads the current content via GET /internal/tickets/{id}.
 */
public record TicketCreado(UUID eventoId, UUID ticketId, Instant ocurridoEn) {

    public static final String TIPO = "ticket.creado";
    public static final int VERSION = 1;
}
