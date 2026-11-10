package com.triageai.tickets.dominio;

/** RN-01: the requested state transition is not allowed (mapped to 409). */
public class TransicionInvalidaException extends RuntimeException {

    private final EstadoTicket origen;

    public TransicionInvalidaException(EstadoTicket origen, EstadoTicket destino) {
        this(origen, "Cannot move from " + origen + " to " + destino + ".");
    }

    public TransicionInvalidaException(EstadoTicket origen, String mensaje) {
        super(mensaje);
        this.origen = origen;
    }

    public EstadoTicket getOrigen() {
        return origen;
    }
}
