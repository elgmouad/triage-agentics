package com.triageai.tickets.dominio;

/** The operation is incompatible with the ticket's current state (mapped to 409). */
public class OperacionNoPermitidaException extends RuntimeException {

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
