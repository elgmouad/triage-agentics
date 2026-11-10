package com.triageai.agente.ia;

/** The model answered, but not with a usable triage (treated as a transient failure). */
public class RespuestaInvalidaException extends RuntimeException {

    public RespuestaInvalidaException(String mensaje) {
        super(mensaje);
    }

    public RespuestaInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
