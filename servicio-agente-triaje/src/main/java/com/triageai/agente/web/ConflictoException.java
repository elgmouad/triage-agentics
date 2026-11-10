package com.triageai.agente.web;

/** Operation incompatible with the ticket's state (409). */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
