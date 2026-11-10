package com.triageai.agente.tickets;

import java.util.UUID;

/** servicio-tickets answered 404 for this ticket. */
public class TicketNoEncontradoException extends RuntimeException {

    public TicketNoEncontradoException(UUID id) {
        super("Ticket " + id + " does not exist.");
    }
}
