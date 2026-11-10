package com.triageai.tickets.dominio;

import java.util.EnumSet;
import java.util.Set;

/**
 * RN-01 — ticket lifecycle. Each state knows which states it may move to;
 * any other transition is rejected by the API with {@code 409 Conflict}.
 */
public enum EstadoTicket {

    NUEVO,
    PENDIENTE_TRIAJE,
    TRIADO,
    EN_CURSO,
    RESUELTO,
    CERRADO;

    public Set<EstadoTicket> transicionesPermitidas() {
        return switch (this) {
            case NUEVO -> EnumSet.of(TRIADO, PENDIENTE_TRIAJE);
            case PENDIENTE_TRIAJE -> EnumSet.of(TRIADO);
            case TRIADO -> EnumSet.of(EN_CURSO);
            case EN_CURSO -> EnumSet.of(RESUELTO);
            case RESUELTO -> EnumSet.of(EN_CURSO, CERRADO);
            case CERRADO -> EnumSet.noneOf(EstadoTicket.class);
        };
    }

    public boolean puedePasarA(EstadoTicket destino) {
        return transicionesPermitidas().contains(destino);
    }

    /** States that can only be reached through the triage process, never by hand. */
    public boolean reservadoAlTriaje() {
        return this == TRIADO || this == PENDIENTE_TRIAJE;
    }

    /** States waiting for a (new) triage result. */
    public boolean esperaTriaje() {
        return this == NUEVO || this == PENDIENTE_TRIAJE;
    }
}
