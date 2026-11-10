package com.triageai.agente.tickets;

import java.util.UUID;

/** The fields of servicio-tickets' TicketResponse the agent needs. */
public record TicketDto(
        UUID id,
        UUID clienteId,
        String titulo,
        String descripcion,
        String estado,
        int intentosTriaje) {

    /** Only NUEVO and PENDIENTE_TRIAJE tickets accept a triage result. */
    public boolean esperaTriaje() {
        return "NUEVO".equals(estado) || "PENDIENTE_TRIAJE".equals(estado);
    }
}
