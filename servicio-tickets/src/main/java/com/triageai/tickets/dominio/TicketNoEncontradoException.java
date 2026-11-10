package com.triageai.tickets.dominio;

import java.util.UUID;

/** Unknown ticket identifier (mapped to 404). */
public class TicketNoEncontradoException extends RuntimeException {

    public TicketNoEncontradoException(UUID id) {
        super("Ticket " + id + " does not exist.");
    }
}
